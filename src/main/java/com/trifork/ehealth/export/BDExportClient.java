package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static ca.uhn.fhir.rest.api.Constants.STATUS_HTTP_202_ACCEPTED;
import static com.trifork.ehealth.export.BDExportUtils.extractContentLocation;

public class BDExportClient {
    private final FhirContext fhirContext;
    private final HapiFhirExportClient exportClient;

    public BDExportClient(FhirContext fhirContext, HttpClient httpClient) {
        this.fhirContext = fhirContext;
        this.exportClient = new HapiFhirExportClient(fhirContext, httpClient);
    }

    /**
     * Begin a Bulk Data Export
     *
     * @param request - Configuration for the export
     * @return a future
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public Future<BDExportResponse> startExport(BDExportRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = exportClient.initiate(request);
        int statusCode = response.statusCode();

        if (statusCode == STATUS_HTTP_202_ACCEPTED) {
            URI pollLocation = extractContentLocation(response);

            return new BDExportFuture(fhirContext, exportClient, pollLocation);
        } else if (statusCode >= 400 && statusCode <= 599) {
            OperationOutcome outcome = fhirContext.newJsonParser()
                    .parseResource(OperationOutcome.class, response.body());

            BDExportResponse exportResponse = new BDExportResponse(statusCode, null, outcome);
            return new ErrorFuture(exportResponse);
        } else {
            throw new RuntimeException("Failed to initiate export, server responded with: " + statusCode);
        }
    }

    /**
     * Resume a Bulk Data Export, given a polling status URI.
     *
     * @param contentLocation - URI of the status for the ongoing export
     * @return a future
     */
    public Future<BDExportResponse> resumeExport(URI contentLocation) {
        return new BDExportFuture(fhirContext, exportClient, contentLocation);
    }

    public static class ErrorFuture implements Future<BDExportResponse> {
        private final BDExportResponse response;

        public ErrorFuture(BDExportResponse response) {
            this.response = response;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return true;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public BDExportResponse get() {
            return response;
        }

        @Override
        public BDExportResponse get(long timeout, @NotNull TimeUnit unit) {
            return response;
        }
    }
}
