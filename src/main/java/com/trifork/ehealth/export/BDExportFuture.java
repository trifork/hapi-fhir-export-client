package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
    private HttpResponse cachedPollResponse;
    private Instant nextPollTime = Instant.now();

    BDExportFuture(
            FhirContext fhirContext,
            HapiFhirExportClient exportClient,
            URI pollingUri
    ) {
        this.fhirContext = fhirContext;
        this.exportClient = exportClient;
        this.pollingUri = pollingUri;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        try {
            if (!isDone()) {
                HttpResponse cancelResponse = exportClient.cancel(pollingUri);

                if (cancelResponse.getStatusLine().getStatusCode() == STATUS_HTTP_202_ACCEPTED) {
                    Thread.currentThread().interrupt();
                    return true;
                }
            }
        } catch (IOException e) {
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
            HttpResponse pollResponse = doCachedPolling();
            return BDExportUtils.isCancelled(pollResponse);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isDone() {
        try {
            HttpResponse pollResponse = doCachedPolling();
            return isDone(pollResponse);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BDExportResponse get() throws InterruptedException, ExecutionException {
        try {
            Optional<BDExportResponse> exportResponseOpt = awaitExportResponse();
            while (exportResponseOpt.isEmpty()) {
                Thread.sleep(calculateSleepTimeInMs());
                exportResponseOpt = awaitExportResponse();
            }

            return exportResponseOpt.get();
        } catch (IOException e) {
            throw new ExecutionException("Failed to fetch 'Bulk Data Export' response", e);
        }
    }

    @Override
    public BDExportResponse get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long timeoutMillis = unit.toMillis(timeout);
        long startTime = System.currentTimeMillis();

        try {
            Optional<BDExportResponse> exportResponseOpt = awaitExportResponse();

            while (exportResponseOpt.isEmpty()) {
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    // Timeout exceeded
                    throw new TimeoutException("Export operation timed out");
                }

                Thread.sleep(calculateSleepTimeInMs());
                exportResponseOpt = awaitExportResponse();
            }

            return exportResponseOpt.get();
        } catch (IOException e) {
            throw new ExecutionException("Failed to fetch 'Bulk Data Export' response", e);
        }
    }

    protected Optional<BDExportResponse> awaitExportResponse() throws IOException {
        HttpResponse response = doCachedPolling();
        if (isDone(response)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == Constants.STATUS_HTTP_200_OK) {
                HttpEntity entity = response.getEntity();
                BDExportResultResponse result = null;

                logger.info("'Bulk Data Export' finished");

                if (entity != null) {
                    InputStream content = entity.getContent();
                    byte[] bytes = content.readAllBytes();

                    logger.info("Reading " + bytes.length + " from 'Bulk Data Export'");

                    if (bytes.length > 0) {
                        try {
                            result = new ObjectMapper().readValue(bytes, BDExportResultResponse.class);
                        } catch (Exception e) {
                            logger.error("Failed to parse response entity", e);
                            // Empty content, so no results.
                        }
                    }
                }

                return Optional.of(
                        new BDExportResponse(pollingUri, statusCode, result, null)
                );
            } else if (statusCode >= 400 && statusCode <= 599) {
                OperationOutcome operationOutcome = null;

                logger.info("'Bulk Data Export' failed with status: " + statusCode);

                HttpEntity entity = response.getEntity();

                if (entity != null) {
                    InputStream content = entity.getContent();
                    operationOutcome = fhirContext.newJsonParser().parseResource(OperationOutcome.class, content);
                }

                return Optional.of(
                        new BDExportResponse(pollingUri, statusCode, null, operationOutcome)
                );
            } else if (isCancelled()) {
                throw new RuntimeException("Bulk Data Export has been cancelled.");
            } else {
                throw new RuntimeException("Bulk Data Export did not finish for unknown reasons.");
            }
        }

        return Optional.empty();
    }

    protected boolean isDone(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        return BDExportUtils.isCancelled(response) || (statusCode != STATUS_HTTP_202_ACCEPTED
                && statusCode != STATUS_HTTP_429_TOO_MANY_REQUESTS);
    }

    protected HttpResponse doCachedPolling() throws IOException {
        boolean pastRetryAfterDuration = Instant.now().isAfter(nextPollTime);

        if (cachedPollResponse == null || (pastRetryAfterDuration && !isDone(cachedPollResponse))) {
            return doPolling();
        }

        return cachedPollResponse;
    }

    protected HttpResponse doPolling() throws IOException {
        this.cachedPollResponse = exportClient.poll(pollingUri);

        Optional<Integer> opt = BDExportUtils.extractRetryAfterInSeconds(cachedPollResponse);
        opt.ifPresent(integer -> this.nextPollTime = BDExportUtils.evaluateNextAllowedPollTime(integer));

        BDExportUtils.extractProgress(cachedPollResponse)
                .ifPresent(s -> logger.info("'Bulk Data Export' status: '" + s + "', next polling at: " + nextPollTime));

        return cachedPollResponse;
    }

    protected long calculateSleepTimeInMs() {
        long diff = (nextPollTime.getEpochSecond() * 1000) - System.currentTimeMillis();
        return Math.max(10000, diff);
    }

    public URI getPollingUri() {
        return pollingUri;
    }

    public void setNextPollTime(Instant time) {
        this.nextPollTime = time;
    }
}
