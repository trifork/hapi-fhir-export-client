package com.trifork.ehealth.export.future;

import com.trifork.ehealth.export.BDExportClient;
import com.trifork.ehealth.export.response.BDCancelResponse;
import com.trifork.ehealth.export.response.BDExportResponse;
import com.trifork.ehealth.export.response.BDPollResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BDInitiatedExportFuture implements BDExportFuture {
    private final BDExportClient exportClient;
    private final URI locationUri;

    private BDPollResponse lastPollResponse;
    private boolean cancelled = false;
    private boolean done = false;
    private BDExportResponse resultResponse;
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    private final Logger logger = LoggerFactory.getLogger(BDInitiatedExportFuture.class);

    public BDInitiatedExportFuture(BDExportClient exportClient, URI locationUri) {
        this.exportClient = exportClient;
        this.locationUri = locationUri;
    }

    public URI getLocationURI() {
        return locationUri;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        try {
            BDCancelResponse cancelResponse = exportClient.cancel(locationUri);
            if (cancelResponse.isAccepted()) {
                countDownLatch.countDown();
                this.cancelled = true;
                this.done = true;
                return true;
            }
        } catch (IOException e) {
            logger.error("Failed to cancel export", e);
        }

        logger.warn("Failed to cancel export; last response status: {}", lastPollResponse.getStatusCode());
        return false;
    }

    @Override
    public boolean isCancelled() {
        poll();
        return cancelled || (lastPollResponse != null && lastPollResponse.isCancelled());
    }

    @Override
    public boolean isDone() {
        poll();
        this.done = cancelled || resultResponse != null || (lastPollResponse != null && lastPollResponse.isDone());
        return done;
    }

    @Override
    public BDExportResponse get() throws InterruptedException {
        try {
            return getInternal(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BDExportResponse get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        return getInternal(timeout, unit);
    }

    private BDExportResponse getInternal(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        long timeoutMillis = unit != null ? unit.toMillis(timeout) : Long.MAX_VALUE;
        long startTime = System.currentTimeMillis();

        while (!isDone()) {
            long currentRunTime = System.currentTimeMillis() - startTime;
            if (currentRunTime > timeoutMillis) {
                throw new TimeoutException("Export operation timed out");
            }

            long timeoutLeft = timeoutMillis - currentRunTime;
            final long nextPollDuration = Math.min(getSleepTimeInMs(), timeoutLeft);

            this.lastPollResponse.getProgressHeader()
                    .ifPresent(progress -> logger.info("'Bulk Data Export' progress: '{}', next polling in: {} seconds", progress, (nextPollDuration / 1000)));

            countDownLatch.await(nextPollDuration, TimeUnit.MILLISECONDS);
        }

        if (cancelled) {
            throw new InterruptedException("Export operation was cancelled");
        }

        if (resultResponse != null) {
            return resultResponse;
        }

        if (lastPollResponse.isError()) {
            resultResponse = new BDExportResponse(locationUri, lastPollResponse.getStatusCode(), null, lastPollResponse.getErrorOpt().orElse(null));
        } else {
            resultResponse = lastPollResponse.getResultOpt()
                    .map(result -> new BDExportResponse(locationUri, lastPollResponse.getStatusCode(), result, null))
                    .orElseThrow(() -> new IllegalStateException("No valid result or error in the response"));
        }

        return resultResponse;
    }

    private void poll() {
        if (!done) {
            try {
                this.lastPollResponse = exportClient.poll(locationUri);

                if (this.lastPollResponse.isDone()) {
                    logger.info("Polling complete. Export is done.");
                    this.done = true;
                    this.cancelled = false;
                } else if (this.lastPollResponse.isCancelled()) {
                    logger.info("Polling complete. Export was cancelled.");
                    this.done = true;
                    this.cancelled = true;
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to poll export status", e);
            }
        }
    }

    /**
     * Get the time to sleep before polling again, with minimum being 10 seconds.
     *
     * @return milliseconds to sleep
     */
    protected Integer getSleepTimeInMs() {
        return lastPollResponse.getRetryAfterInSecondsOpt().map(seconds -> seconds * 1000)
                .orElse(10000);
    }
}
