package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.*;

import static ca.uhn.fhir.rest.api.Constants.STATUS_HTTP_202_ACCEPTED;

public class BDExportFuture implements Future<BDExportResponse> {
    private static final Logger logger = LoggerFactory.getLogger(BDExportFuture.class);
    private static final int STATUS_HTTP_429_TOO_MANY_REQUESTS = 429;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final FhirContext fhirContext;
    private final HapiFhirExportClient exportClient;
    private final URI pollingUri;

    // Respect the retry-after, and cache the result, so we don't get rate limited in HAPI FHIR.
    private HttpResponse cachedPollResponse;
    private Instant nextPollTime = Instant.now();
    private int retryAfterInSeconds = 10;

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
            int statusCode = pollResponse.getStatusLine().getStatusCode();
            return isCancelled() || (statusCode != STATUS_HTTP_202_ACCEPTED
                    && statusCode != STATUS_HTTP_429_TOO_MANY_REQUESTS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BDExportResponse get() throws InterruptedException, ExecutionException {
        Optional<BDExportResponse> exportResponseOpt = getPollingFuture().get();
        while (exportResponseOpt.isEmpty()) {
            exportResponseOpt = getPollingFuture().get();
        }

        return exportResponseOpt.get();
    }

    @Override
    public BDExportResponse get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        Optional<BDExportResponse> exportResponseOpt = getPollingFuture().get(timeout, unit);
        while (exportResponseOpt.isEmpty()) {
            exportResponseOpt = getPollingFuture().get(timeout, unit);
        }

        return exportResponseOpt.get();
    }

    protected Future<Optional<BDExportResponse>> getPollingFuture() {
        return scheduler.schedule(this::awaitExportResponse, this.retryAfterInSeconds, TimeUnit.SECONDS);
    }

    protected Optional<BDExportResponse> awaitExportResponse() throws IOException {
        if (isDone()) {
            HttpResponse response = doCachedPolling();
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 200) {
                HttpEntity entity = response.getEntity();
                BDExportResultResponse result = null;

                if (entity.getContentLength() > 0) {
                    InputStream content = entity.getContent();
                    result = new ObjectMapper().readValue(content, BDExportResultResponse.class);
                }

                return Optional.of(
                        new BDExportResponse(pollingUri, statusCode, result, null)
                );
            } else if (statusCode >= 400 && statusCode <= 599) {
                OperationOutcome operationOutcome = null;

                HttpEntity entity = response.getEntity();
                if (entity.getContentLength() > 0) {
                    InputStream content = entity.getContent();
                    operationOutcome = fhirContext.newJsonParser().parseResource(OperationOutcome.class, content);
                }

                return Optional.of(
                        new BDExportResponse(pollingUri, statusCode, null, operationOutcome)
                );
            }
        }

        return Optional.empty();
    }

    protected HttpResponse doCachedPolling() throws IOException {
        boolean pastRetryAfterDuration = Instant.now().isAfter(nextPollTime);

        if (cachedPollResponse == null || pastRetryAfterDuration) {
            return doPolling();
        }

        return cachedPollResponse;
    }

    protected HttpResponse doPolling() throws IOException {
        this.cachedPollResponse = exportClient.poll(pollingUri);

        if (cachedPollResponse.getStatusLine().getStatusCode() == STATUS_HTTP_202_ACCEPTED) {
            Optional<Integer> opt = BDExportUtils.extractRetryAfterInSeconds(cachedPollResponse);
            if (opt.isPresent()) {
                this.retryAfterInSeconds = opt.get();
                this.nextPollTime = BDExportUtils.evaluateNextAllowedPollTime(retryAfterInSeconds);
            }
        }

        BDExportUtils.extractProgress(cachedPollResponse)
                .ifPresent(s -> logger.info("'Bulk Data Export' status: '" + s + "'"));

        return cachedPollResponse;
    }

    public URI getPollingUri() {
        return pollingUri;
    }

    public void setRetryInterval(int seconds) {
        this.retryAfterInSeconds = seconds;
    }
}
