package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static ca.uhn.fhir.rest.api.Constants.STATUS_HTTP_202_ACCEPTED;

public class BDExportFuture implements Future<BDExportResponse> {
    private static final Logger logger = LoggerFactory.getLogger(BDExportFuture.class);
    private static final int STATUS_HTTP_429_TOO_MANY_REQUESTS = 429;

    private final FhirContext fhirContext;
    private final HapiFhirExportClient exportClient;
    private final URI pollingUri;

    // Respect the retry-after, and cache the result, so we don't get rate limited in HAPI FHIR.
    private HttpResponse<String> cachedPollResponse;
    private Instant nextPollTime = Instant.now();

    BDExportFuture(FhirContext fhirContext, HapiFhirExportClient exportClient, URI pollingUri) {
        this.fhirContext = fhirContext;
        this.exportClient = exportClient;
        this.pollingUri = pollingUri;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        try {
            if (!isDone()) {
                HttpResponse<String> cancelResponse = exportClient.cancel(pollingUri);

                if (cancelResponse.statusCode() == STATUS_HTTP_202_ACCEPTED) {
                    Thread.currentThread().interrupt();
                    return true;
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Could not cancel export", e);
        }

        return false;
    }

    @Override
    public boolean isCancelled() {
        try {
            /*
              HAPI FHIR takes a while to cancel ongoing export jobs,
              so it still makes sense to use cached results here.
             */
            HttpResponse<String> pollResponse = doCachedPolling();
            return BDExportUtils.isCancelled(pollResponse.headers());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isDone() {
        try {
            HttpResponse<String> pollResponse = doCachedPolling();
            return isCancelled() || (pollResponse.statusCode() != STATUS_HTTP_202_ACCEPTED
                    && pollResponse.statusCode() != STATUS_HTTP_429_TOO_MANY_REQUESTS);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BDExportResponse get() throws InterruptedException, ExecutionException {
        try {
            Optional<BDExportResponse> exportResponseOpt = awaitExportResponse();
            while (exportResponseOpt.isEmpty()) {
                Thread.sleep(1000);
                exportResponseOpt = awaitExportResponse();
            }

            return exportResponseOpt.get();
        } catch (IOException e) {
            throw new ExecutionException("Failed to fetch 'Bulk Data Export' response", e);
        }
    }

    @Override
    public BDExportResponse get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long timeoutMillis = unit.toMillis(timeout);
        long startTime = System.currentTimeMillis();

        try {
            Optional<BDExportResponse> exportResponseOpt = awaitExportResponse();

            while (exportResponseOpt.isEmpty()) {
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    // Timeout exceeded
                    throw new TimeoutException("Export operation timed out");
                }

                Thread.sleep(1000);
                exportResponseOpt = awaitExportResponse();
            }

            return exportResponseOpt.get();
        } catch (IOException e) {
            throw new ExecutionException("Failed to fetch 'Bulk Data Export' response", e);
        }
    }

    protected Optional<BDExportResponse> awaitExportResponse() throws IOException, InterruptedException {
        if (isDone()) {
            HttpResponse<String> response = doCachedPolling();
            int statusCode = response.statusCode();

            if (statusCode == 200) {
                BDExportResultResponse result = new ObjectMapper().readValue(response.body(), BDExportResultResponse.class);

                return Optional.of(
                        new BDExportResponse(statusCode, result, null)
                );
            } else if (statusCode >= 400 && statusCode <= 599) {
                OperationOutcome operationOutcome = fhirContext.newJsonParser()
                        .parseResource(OperationOutcome.class, response.body());

                return Optional.of(
                        new BDExportResponse(statusCode, null, operationOutcome)
                );
            }
        }

        return Optional.empty();
    }

    protected HttpResponse<String> doCachedPolling() throws IOException, InterruptedException {
        boolean pastRetryAfterDuration = Instant.now().isAfter(nextPollTime);

        if (cachedPollResponse == null || pastRetryAfterDuration) {
            return doPolling();
        }

        return cachedPollResponse;
    }

    protected HttpResponse<String> doPolling() throws IOException, InterruptedException {
        this.cachedPollResponse = exportClient.poll(pollingUri);
        this.nextPollTime = BDExportUtils.evaluateNextAllowedPollTime(cachedPollResponse.headers());

        Optional<String> progress = BDExportUtils.extractProgress(cachedPollResponse.headers());
        progress.ifPresent(s -> logger.info("'Bulk Data Export' status: '" + s + "'"));

        return cachedPollResponse;
    }

    public URI getPollingUri() {
        return pollingUri;
    }
}
