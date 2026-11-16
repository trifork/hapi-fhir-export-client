package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import com.trifork.ehealth.export.exceptions.BDExportFailedException;
import com.trifork.ehealth.export.exceptions.BDExportTimeoutException;
import com.trifork.ehealth.export.future.BDExportFuture;
import com.trifork.ehealth.export.response.BDExportResponse;
import com.trifork.ehealth.export.response.BDExportResultResponse;
import com.trifork.ehealth.export.response.BDExportResultResponse.OutputItem;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class TestBDExportRunner {
    private static final URI BASE_URI = URI.create("http://localhost:8080/fhir/");
    private static final URI STATUS_URI = URI.create("http://localhost:8080/fhir/$export-poll-status?jobId=1337");
    private static final Duration TIMEOUT = Duration.ofMinutes(15);
    private static final Duration POLLING_INTERVAL = Duration.ofSeconds(5);

    private final FhirContext fhirContext = FhirContext.forR4();
    private final BDExportClient client = mock(BDExportClient.class);
    private final BDExportFuture future = mock(BDExportFuture.class);
    private final BDExportRequest request = BDExportRequest.createSystemExportRequest(BASE_URI)
            .addType(ResourceType.Patient);

    @BeforeEach
    void setUp() throws IOException {
        when(client.initiate(request)).thenReturn(future);
        when(future.getLocationURI()).thenReturn(STATUS_URI);
    }

    @Test
    void returns_the_output_items_in_order() throws Exception {
        OutputItem first = new OutputItem("Patient", "http://localhost:8080/fhir/Binary/1");
        OutputItem second = new OutputItem("Patient", "http://localhost:8080/fhir/Binary/2");
        finishesWith(result(List.of(first, second), List.of()));

        List<OutputItem> output = run();

        assertEquals(List.of(first, second), output);
    }

    @Test
    void returns_an_empty_list_when_the_result_has_no_output() throws Exception {
        finishesWith(result(null, null));

        assertEquals(List.of(), run());
    }

    @Test
    void sets_the_polling_interval_in_milliseconds() throws Exception {
        finishesWith(result(List.of(), List.of()));

        run();

        verify(future).setPollingInterval(5000);
    }

    @Test
    void waits_for_the_given_timeout() throws Exception {
        finishesWith(result(List.of(), List.of()));

        run();

        verify(future).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void an_error_in_the_response_fails_the_export_with_the_outcome_in_the_message() throws Exception {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setDiagnostics("the job was rejected");
        when(future.get(anyLong(), any(TimeUnit.class)))
                .thenReturn(new BDExportResponse(STATUS_URI, 500, null, outcome));

        BDExportFailedException thrown = assertThrows(BDExportFailedException.class, this::run);

        assertTrue(thrown.getMessage().contains(STATUS_URI.toString()), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("the job was rejected"), thrown.getMessage());
    }

    @Test
    void error_items_in_the_result_fail_the_export_with_their_urls() throws Exception {
        OutputItem error = new OutputItem("OperationOutcome", "http://localhost:8080/fhir/Binary/error-1");
        finishesWith(result(List.of(), List.of(error)));

        BDExportFailedException thrown = assertThrows(BDExportFailedException.class, this::run);

        assertTrue(thrown.getMessage().contains("http://localhost:8080/fhir/Binary/error-1"), thrown.getMessage());
    }

    @Test
    void a_response_without_result_and_error_fails_the_export() throws Exception {
        when(future.get(anyLong(), any(TimeUnit.class)))
                .thenReturn(new BDExportResponse(STATUS_URI, 200, null, null));

        BDExportFailedException thrown = assertThrows(BDExportFailedException.class, this::run);

        assertTrue(thrown.getMessage().contains("neither a result nor an error"), thrown.getMessage());
    }

    @Test
    void a_timeout_throws_a_timeout_exception_with_the_status_url_and_does_not_cancel() throws Exception {
        TimeoutException timeout = new TimeoutException("Export operation timed out");
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(timeout);

        BDExportTimeoutException thrown = assertThrows(BDExportTimeoutException.class, this::run);

        assertSame(timeout, thrown.getCause());
        assertTrue(thrown.getMessage().contains(STATUS_URI.toString()), thrown.getMessage());
        verify(future, never()).cancel(anyBoolean());
        verify(client, never()).cancel(any());
    }

    @Test
    void a_failure_while_waiting_fails_the_export_with_the_cause() throws Exception {
        IllegalStateException cause = new IllegalStateException("poll failed");
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new ExecutionException(cause));

        BDExportFailedException thrown = assertThrows(BDExportFailedException.class, this::run);

        assertSame(cause, thrown.getCause());
    }

    @Test
    void failing_to_start_the_export_is_an_io_exception() throws Exception {
        IOException failure = new IOException("connection refused");
        when(client.initiate(request)).thenThrow(failure);

        IOException thrown = assertThrows(IOException.class, this::run);

        assertSame(failure, thrown);
    }

    @Test
    void an_interrupt_while_waiting_passes_through() throws Exception {
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("interrupted"));

        InterruptedException thrown = assertThrows(InterruptedException.class, this::run);

        assertInstanceOf(InterruptedException.class, thrown);
        verify(future, never()).cancel(anyBoolean());
    }

    private List<OutputItem> run() throws IOException, InterruptedException {
        return BDExportRunner.exportAndWait(client, request, TIMEOUT, POLLING_INTERVAL, fhirContext);
    }

    private void finishesWith(BDExportResultResponse result) throws Exception {
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(new BDExportResponse(STATUS_URI, 200, result, null));
    }

    private static BDExportResultResponse result(List<OutputItem> output, List<OutputItem> error) {
        return new BDExportResultResponse("2026-11-16T08:00:00Z", "http://localhost:8080/fhir/$export", false, output, error, null);
    }
}
