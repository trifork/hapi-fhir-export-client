package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static ca.uhn.fhir.rest.api.Constants.STATUS_HTTP_202_ACCEPTED;
import static com.trifork.ehealth.export.BDExportUtils.extractContentLocation;

public class BDExportClient {
    private final FhirContext fhirContext;
    private final HapiFhirExportClient exportClient;

    private static final Logger logger = LoggerFactory.getLogger(BDExportClient.class);

    public BDExportClient(FhirContext fhirContext, HapiFhirExportClient exportClient) {
        this.fhirContext = fhirContext;
        this.exportClient = exportClient;
    }

    /**
     * Begin a Bulk Data Export
     *
     * @param request - Configuration for the export
     * @return a future
     * @throws IOException
     * @throws InterruptedException
     */
    public Future<BDExportResponse> startExport(BDExportRequest request) throws IOException {
        HttpResponse response = exportClient.initiate(request);
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode == STATUS_HTTP_202_ACCEPTED) {
            URI pollLocation = extractContentLocation(response);

            return new BDExportFuture(fhirContext, exportClient, pollLocation);
        } else if (statusCode >= 400 && statusCode <= 599) {
            OperationOutcome outcome = null;

            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try {
                    InputStream content = entity.getContent();
                    outcome = fhirContext.newJsonParser()
                            .parseResource(OperationOutcome.class, content);
                } catch (Exception e) {
                    logger.error("Failed to parse operation outcome content.");
                }
            } else {
                logger.info("Received empty response body, and status code: " + statusCode);
            }

            BDExportResponse exportResponse = new BDExportResponse(request.getExportUri(), statusCode, null, outcome);
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
        public BDExportResponse get(long timeout, TimeUnit unit) {
            return response;
        }
    }
}
