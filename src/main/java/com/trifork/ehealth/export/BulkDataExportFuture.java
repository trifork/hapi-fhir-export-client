package com.trifork.ehealth.export;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BulkDataExportFuture implements Future<BDExportResponse> {
    private final BDExportClient exportClient;
    private final BDExportRequest request;
    private URI pollingUri;

    public BulkDataExportFuture(BDExportClient exportClient, BDExportRequest request) {
        this.exportClient = exportClient;
        this.request = request;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
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
}
