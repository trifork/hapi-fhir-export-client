package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trifork.ehealth.export.response.BDExportResponse;
import com.trifork.ehealth.export.response.BDExportResultResponse;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class TestBDExportClient {
    private HttpClient httpClient;
    private HttpResponse initateResponse;
    private HttpResponse pollResponse;
    private BDExportClient exportClient;
    private final FhirContext fhirContext = FhirContext.forR4();

    private static final URI exportUri = URI.create("http://localhost:8080/fhir/$export");
    private static final URI pollUri = URI.create("http://localhost:8080/fhir/$export-poll-status?jobId=1337");
    private final HttpRequestUrlMatcher exportUriMatcher = new HttpRequestUrlMatcher(exportUri);
    private final HttpRequestUrlMatcher pollUriMatcher = new HttpRequestUrlMatcher(pollUri);

    @BeforeEach
    void setup() throws IOException {
        this.httpClient = mock(HttpClient.class);
        ProtocolVersion protocolVersion = new ProtocolVersion("http", 1, 1);
        BasicStatusLine notFound = new BasicStatusLine(protocolVersion, 404, "Not found");
        this.initateResponse = spy(new BasicHttpResponse(notFound));
        this.pollResponse = spy(new BasicHttpResponse(notFound));

        doReturn(initateResponse).when(httpClient).execute(argThat(exportUriMatcher));
        doReturn(pollResponse).when(httpClient).execute(argThat(pollUriMatcher));

        this.exportClient = new BDExportClient(fhirContext, httpClient);
    }

    @Test
    void export_initiation_fails() throws IOException, InterruptedException, ExecutionException {
        OperationOutcome operationOutcome = new OperationOutcome();
        operationOutcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.INFORMATION);

        BasicHttpEntity entity = new BasicHttpEntity();
        IParser jsonParser = fhirContext.newJsonParser();
        entity.setContent(new ByteArrayInputStream(jsonParser.encodeResourceToString(operationOutcome).getBytes(StandardCharsets.UTF_8)));
        doReturn(entity).when(initateResponse).getEntity();
        doReturn(new BasicStatusLine(
                new ProtocolVersion("http", 1, 1),
                Constants.STATUS_HTTP_422_UNPROCESSABLE_ENTITY,
                "Unprocessable Entity")
        ).when(initateResponse).getStatusLine();

        Future<BDExportResponse> future = exportClient.initiate(new BDExportRequest(exportUri));

        assertTrue(future.isDone());
        BDExportResponse bdExportResponse = future.get();

        assertFalse(bdExportResponse.getResult().isPresent());
        assertTrue(bdExportResponse.getError().isPresent());
        assertEquals(jsonParser.encodeResourceToString(operationOutcome), jsonParser.encodeResourceToString(bdExportResponse.getError().get()));

        verify(httpClient, atMostOnce()).execute(argThat(exportUriMatcher));
    }

    @Test
    void export_initiation_returns_a_future_in_progress() throws IOException {
        configureExportInitiation();
        configurePollInProgress();

        Future<BDExportResponse> future = exportClient.initiate(new BDExportRequest(exportUri));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        verify(httpClient, atMostOnce()).execute(argThat(exportUriMatcher));
        verify(httpClient, atLeastOnce()).execute(argThat(pollUriMatcher));
    }

    @Test
    void export_has_finished() throws IOException, InterruptedException, ExecutionException {
        configureExportInitiation();
        configurePollInProgress();

        Future<BDExportResponse> future = exportClient.initiate(new BDExportRequest(exportUri));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        BDExportResultResponse expectedResult = new BDExportResultResponse(
                "1337",
                exportUri.toString(),
                false,
                Collections.singletonList(new BDExportResultResponse.OutputItem("Binary", "url")),
                Collections.emptyList(),
                ""
        );

        configurePollHasFinished(expectedResult);

        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        BDExportResponse response = future.get();
        assertTrue(response.getResult().isPresent());
        assertEquals(expectedResult, response.getResult().get());

        verify(httpClient, atMostOnce()).execute(argThat(exportUriMatcher));
        verify(httpClient, atLeastOnce()).execute(argThat(pollUriMatcher));
    }

    @Test
    void export_has_finished_with_no_results() throws IOException, InterruptedException, ExecutionException {
        configureExportInitiation();
        configurePollInProgress();

        Future<BDExportResponse> future = exportClient.initiate(new BDExportRequest(exportUri));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        configurePollHasFinishedWithNoResults();

        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        BDExportResponse response = future.get();
        assertTrue(response.getResult().isPresent());
        assertTrue(response.getResult().get().getOutput().isEmpty());
        assertFalse(response.getError().isPresent());

        verify(httpClient, atMostOnce()).execute(argThat(exportUriMatcher));
        verify(httpClient, atLeastOnce()).execute(argThat(pollUriMatcher));
    }

    @Test
    void export_has_been_cancelled() throws IOException {
        configureExportInitiation();
        configurePollInProgress();

        Future<BDExportResponse> future = exportClient.initiate(new BDExportRequest(exportUri));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        configurePollHasBeenCancelled();

        assertTrue(future.isCancelled());
        assertTrue(future.isDone());
    }

    @Test
    void export_throws_error_during_polling() throws IOException {
        configureExportInitiation();
        configurePollInProgress();

        Future<BDExportResponse> future = exportClient.initiate(new BDExportRequest(exportUri));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        configurePollThrowsErrorDuringExport();

        assertFalse(future.isCancelled());
        assertTrue(future.isDone());
    }

    @Test
    void export_times_out() throws IOException, InterruptedException, ExecutionException {
        configureExportInitiation();
        configurePollInProgress();

        Future<BDExportResponse> future = exportClient.initiate(new BDExportRequest(exportUri));

        try {
            future.get(1, TimeUnit.SECONDS);
            fail("Expected timeout exception");
        } catch (TimeoutException e) {
        }
    }

    private void configureExportInitiation() {
        initateResponse.setHeader("content-location", pollUri.toString());
        initateResponse.setStatusCode(Constants.STATUS_HTTP_202_ACCEPTED);
    }

    private void configurePollInProgress() {
        pollResponse.setStatusCode(Constants.STATUS_HTTP_202_ACCEPTED);
        pollResponse.setHeader("x-progress", "In PROGRESS");
        pollResponse.setHeader("retry-after", "2");
    }

    private void configurePollHasFinished(BDExportResultResponse expectedResult) throws JsonProcessingException {
        pollResponse.setStatusCode(Constants.STATUS_HTTP_200_OK);
        pollResponse.setHeader("Content-Type", Constants.CT_JSON);
        BasicHttpEntity httpEntity = new BasicHttpEntity();
        String body = new ObjectMapper().writeValueAsString(expectedResult);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        httpEntity.setContent(new ByteArrayInputStream(bytes));
        httpEntity.setContentLength(bytes.length);
        pollResponse.setEntity(httpEntity);
    }

    private void configurePollHasFinishedWithNoResults() throws JsonProcessingException {
        pollResponse.setStatusCode(Constants.STATUS_HTTP_200_OK);
        pollResponse.setHeader("Content-Type", Constants.CT_JSON);
        BasicHttpEntity httpEntity = new BasicHttpEntity();
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "transactionTime", "2024-07-04T06:38:54.796+00:00",
                "request", "http://localhost:8080/fhir/$export",
                "requiresAccessToken", false,
                "output", Collections.emptyList(),
                "error", Collections.emptyList(),
                "message", "Export complete, but no data to generate report for job"
        ));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        httpEntity.setContent(new ByteArrayInputStream(bytes));
        pollResponse.setEntity(httpEntity);
    }

    private void configurePollThrowsErrorDuringExport() {
        pollResponse.setStatusCode(Constants.STATUS_HTTP_400_BAD_REQUEST);
        pollResponse.setHeader("x-progress", "FAILED");
        pollResponse.setEntity(null);
    }

    private void configurePollHasBeenCancelled() {
        pollResponse.setStatusCode(Constants.STATUS_HTTP_202_ACCEPTED);
        pollResponse.setHeader("x-progress", "Build in progress - Status set to CANCELLED at 2023-11-24T09:15:42.295+00:00");
    }

    static class HttpRequestUrlMatcher implements ArgumentMatcher<HttpUriRequest> {
        private final URI expectedUri;

        public HttpRequestUrlMatcher(URI expectedUri) {
            this.expectedUri = expectedUri;
        }

        @Override
        public boolean matches(HttpUriRequest request) {
            return expectedUri.equals(request.getURI());
        }
    }
}
