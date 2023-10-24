package com.trifork.ehealth.export;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BDExportFuture implements Future<BDExportResponse> {
    private final HapiFhirExportClient exportClient;
    private final BDExportRequest request;

    private URI pollingUri;
    private boolean running;

    public BDExportFuture(HapiFhirExportClient exportClient, BDExportRequest request) {
        this.exportClient = exportClient;
        this.request = request;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        if (pollingUri != null) {
            try {
                HttpResponse<String> pollResponse = exportClient.poll(pollingUri);
                return BDExportUtils.isCancelled(pollResponse.headers());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return false;
    }

    @Override
    public boolean isDone() {
        if (pollingUri != null) {

        }

        return false;
    }

    @Override
    public BDExportResponse get() throws InterruptedException, ExecutionException {
        return null;
    }

    @Override
    public BDExportResponse get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return null;
    }

    protected URI getPollingUri() {
        return pollingUri;
    }

    protected boolean isRunning() {
        return running;
    }
}
