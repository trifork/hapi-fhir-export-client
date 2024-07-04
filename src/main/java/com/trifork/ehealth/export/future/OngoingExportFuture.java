package com.trifork.ehealth.export.future;

import ca.uhn.fhir.context.FhirContext;
import com.trifork.ehealth.export.BDExportClient;
import com.trifork.ehealth.export.BDExportUtils;
import com.trifork.ehealth.export.response.BDExportResponse;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static ca.uhn.fhir.rest.api.Constants.STATUS_HTTP_202_ACCEPTED;
import static com.trifork.ehealth.export.BDExportUtils.extractContentLocation;

public class OngoingExportFuture implements BDExportFuture {
    private final FhirContext fhirContext;
    private final BDExportClient exportClient;

    private BDExportFuture delegate;
    private HttpResponse lastResponse;
    private Instant nextPollTime = Instant.now();
    private URI locationUri;
    private boolean cancelled;

    private final Logger logger = LoggerFactory.getLogger(OngoingExportFuture.class);
    private static final int STATUS_HTTP_429_TOO_MANY_REQUESTS = 429;

    public OngoingExportFuture(FhirContext fhirContext, HttpClient httpClient, URI locationUri) {
        this.fhirContext = fhirContext;
        this.locationUri = locationUri;
        this.exportClient = new BDExportClient(fhirContext, httpClient);

        try {
            this.lastResponse = exportClient.poll(locationUri);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public URI getLocationURI() {
        return locationUri;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (delegate != null) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        try {
            HttpResponse cancelResponse = exportClient.cancel(getLocationURI());

            if (BDExportUtils.extractStatusCode(cancelResponse) == STATUS_HTTP_202_ACCEPTED) {
                Thread.currentThread().interrupt();
                this.cancelled = true;
                return true;
            }
        } catch (IOException e) {
            logger.error("Failed to cancel export", e);
            return false;
        }

        logger.error("Failed to cancel export, server responded with: " + BDExportUtils.extractStatusCode(lastResponse));
        return false;
    }

    @Override
    public boolean isCancelled() {
        if (delegate != null) {
            return delegate.isCancelled();
        }

        // HAPI FHIR takes a while to cancel ongoing export jobs, so let us attempt to poll for a status.
        poll();
        return BDExportUtils.isCancelled(lastResponse) || cancelled;
    }

    @Override
    public boolean isDone() {
        if (delegate != null) {
            return delegate.isDone();
        }

        // Get latest response
        poll();
        return isCancelled() || isLastResponseDone();
    }

    private boolean isLastResponseDone() {
        int statusCode = lastResponse.getStatusLine().getStatusCode();
        return statusCode != STATUS_HTTP_202_ACCEPTED && statusCode != STATUS_HTTP_429_TOO_MANY_REQUESTS;
    }

    @Override
    public BDExportResponse get() throws InterruptedException, ExecutionException {
        while (delegate == null) {
            if (isCancelled()) {
                throw new InterruptedException("Export operation was cancelled");
            }

            Thread.sleep(calculateSleepTimeInMs());
            poll();
        }

        return delegate.get();
    }

    @Override
    public BDExportResponse get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long timeoutMillis = unit.toMillis(timeout);
        long startTime = System.currentTimeMillis();

        while (delegate == null) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                // Timeout exceeded
                throw new TimeoutException("Export operation timed out");
            }

            if (isCancelled()) {
                throw new InterruptedException("Export operation was cancelled");
            }

            Thread.sleep(calculateSleepTimeInMs());
            poll();
        }

        return delegate.get(timeout, unit);
    }

    private void poll() {
        boolean pastRetryAfterDuration = Instant.now().isAfter(nextPollTime);

        if (pastRetryAfterDuration && !isLastResponseDone()) {
            this.locationUri = extractContentLocation(lastResponse).orElse(locationUri);

            try {
                this.lastResponse = exportClient.poll(locationUri);

                Optional<Integer> opt = BDExportUtils.extractRetryAfterInSeconds(lastResponse);
                opt.ifPresent(integer -> this.nextPollTime = BDExportUtils.evaluateNextAllowedPollTime(integer));

                BDExportUtils.extractProgress(lastResponse)
                        .ifPresent(s -> logger.info("'Bulk Data Export' status: '" + s + "', next polling at: " + nextPollTime));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if(isLastResponseDone()) {
            this.delegate = exportClient.createFuture(lastResponse, locationUri);
        }
    }

    protected long calculateSleepTimeInMs() {
        long diff = (nextPollTime.getEpochSecond() * 1000) - System.currentTimeMillis();
        return Math.max(10000, diff);
    }

    public void setNextPollTime(Instant time) {
        this.nextPollTime = time;
    }
}
