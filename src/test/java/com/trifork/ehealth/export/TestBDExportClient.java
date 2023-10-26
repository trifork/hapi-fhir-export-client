package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static javolution.testing.TestContext.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class TestBDExportClient {
    private HttpClient httpClient;
    private HttpResponse<String> initateResponse;
    private HttpResponse<String> pollResponse;
    private BDExportClient exportClient;
    private final FhirContext fhirContext = FhirContext.forR4();

    private static final URI exportUri = URI.create("http://localhost:8080/fhir/$export");
    private static final URI pollUri = URI.create("http://localhost:8080/fhir/$export-poll-status?jobId=1337");
    private final HttpRequestUrlMatcher exportUriMatcher = new HttpRequestUrlMatcher(exportUri);
    private final HttpRequestUrlMatcher pollUriMatcher = new HttpRequestUrlMatcher(pollUri);

    @BeforeEach
    void setup() throws IOException, InterruptedException {
        this.httpClient = mock(HttpClient.class);
        this.initateResponse = mock(HttpResponse.class);
        this.pollResponse = mock(HttpResponse.class);

        doReturn(initateResponse).when(httpClient).send(argThat(exportUriMatcher), any(HttpResponse.BodyHandler.class));
        doReturn(pollResponse).when(httpClient).send(argThat(pollUriMatcher), any(HttpResponse.BodyHandler.class));

        this.exportClient = new BDExportClient(fhirContext, httpClient);
    }

    @Test
    void export_initiation_fails() throws IOException, InterruptedException, ExecutionException {
        OperationOutcome operationOutcome = new OperationOutcome();
        operationOutcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.INFORMATION);

        doReturn(fhirContext.newJsonParser().encodeResourceToString(operationOutcome)).when(initateResponse).body();
        doReturn(Constants.STATUS_HTTP_422_UNPROCESSABLE_ENTITY).when(initateResponse).statusCode();

        Future<BDExportResponse> future = exportClient.startExport(new BDExportRequest(exportUri));

        assertTrue(future.isDone());
        BDExportResponse bdExportResponse = future.get();

        assertFalse(bdExportResponse.getResult().isPresent());
        assertTrue(bdExportResponse.getError().isPresent());
        assertEquals(operationOutcome, bdExportResponse.getError().get());

        verify(httpClient, atMostOnce()).send(argThat(exportUriMatcher), any());
    }

    @Test
    void export_initiation_returns_a_future_in_progress() throws IOException, InterruptedException {
        configureExportInitiation();
        configurePollInProgress();

        Future<BDExportResponse> future = exportClient.startExport(new BDExportRequest(exportUri));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        verify(httpClient, atMostOnce()).send(argThat(exportUriMatcher), any());
        verify(httpClient, atLeastOnce()).send(argThat(pollUriMatcher), any());
    }

    @Test
    void export_has_finished() throws IOException, InterruptedException, ExecutionException {
        configureExportInitiation();
        configurePollInProgress();

        Future<BDExportResponse> future = exportClient.startExport(new BDExportRequest(exportUri));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        BDExportCompleteResult expectedResult = new BDExportCompleteResult(
                "1337",
                exportUri.toString(),
                false,
                Collections.singletonList(new BDExportCompleteResult.OutputItem("Binary", "url")),
                Collections.emptyList(),
                Collections.emptyMap()
        );
        configurePollHasFinished(expectedResult);

        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        BDExportResponse response = future.get();
        assertTrue(response.getResult().isPresent());
        assertEquals(expectedResult, response.getResult().get());

        verify(httpClient, atMostOnce()).send(argThat(exportUriMatcher), any());
        verify(httpClient, atLeastOnce()).send(argThat(pollUriMatcher), any());
    }

    @Test
    void export_has_been_cancelled() throws IOException, InterruptedException {
        configureExportInitiation();
        configurePollInProgress();

        Future<BDExportResponse> future = exportClient.startExport(new BDExportRequest(exportUri));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        configurePollHasBeenCancelled();

        assertTrue(future.isCancelled());
        assertTrue(future.isDone());
    }

    @Test
    void export_times_out() throws IOException, InterruptedException, ExecutionException {
        configureExportInitiation();
        configurePollInProgress();

        Future<BDExportResponse> future = exportClient.startExport(new BDExportRequest(exportUri));

        try {
            future.get(1, TimeUnit.SECONDS);
            fail("Expected timeout exception");
        } catch (TimeoutException e) {}
    }

    private void configureExportInitiation() {
        Map<String, List<String>> headerMap = Map.of("content-location", Collections.singletonList(pollUri.toString()));
        HttpHeaders headers = createHeaders(headerMap);

        doReturn(Constants.STATUS_HTTP_202_ACCEPTED).when(initateResponse).statusCode();
        doReturn(headers).when(initateResponse).headers();
    }

    private void configurePollInProgress() {
        doReturn(Constants.STATUS_HTTP_202_ACCEPTED).when(pollResponse).statusCode();
        doReturn(createHeaders(Map.of("x-progress", Collections.singletonList("In PROGRESS")))).when(pollResponse).headers();
    }

    private void configurePollHasFinished(BDExportCompleteResult expectedResult) throws JsonProcessingException {
        doReturn(Constants.STATUS_HTTP_200_OK).when(pollResponse).statusCode();
        doReturn(createHeaders(Map.of("Content-Type", Collections.singletonList("application/json")))).when(pollResponse).headers();

        String body = new ObjectMapper().writeValueAsString(expectedResult);
        doReturn(body).when(pollResponse).body();
    }

    private void configurePollHasBeenCancelled() {
        doReturn(Constants.STATUS_HTTP_202_ACCEPTED).when(pollResponse).statusCode();
        doReturn(createHeaders(Map.of("x-progress", Collections.singletonList("CANCELLED")))).when(pollResponse).headers();
    }

    private HttpHeaders createHeaders(Map<String, List<String>> map) {
        return HttpHeaders.of(map, (s, s2) -> true);
    }

    static class HttpRequestUrlMatcher implements ArgumentMatcher<HttpRequest> {
        private final URI expectedUri;

        public HttpRequestUrlMatcher(URI expectedUri) {
            this.expectedUri = expectedUri;
        }

        @Override
        public boolean matches(HttpRequest request) {
            return request.uri().equals(expectedUri);
        }
    }
}
