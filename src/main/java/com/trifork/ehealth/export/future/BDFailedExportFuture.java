package com.trifork.ehealth.export.future;

import ca.uhn.fhir.context.FhirContext;
import com.trifork.ehealth.export.response.BDExportResponse;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public class BDFailedExportFuture implements BDExportFuture {
    private final FhirContext fhirContext;
    private final int statusCode;
    private final String body;

    public BDFailedExportFuture(FhirContext fhirContext, int statusCode, String body) {
        this.fhirContext = fhirContext;
        this.statusCode = statusCode;
        this.body = body;

        assert statusCode >= 400 && statusCode <= 599;
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
        return true;
    }

    @Override
    public BDExportResponse get() {
        return createErrorResponse();
    }

    @Override
    public BDExportResponse get(long timeout, TimeUnit unit) {
        return createErrorResponse();
    }

    private BDExportResponse createErrorResponse() {
        OperationOutcome operationOutcome = null;

        if (body != null) {
            operationOutcome = fhirContext.newJsonParser().parseResource(OperationOutcome.class, body);
        }

        return new BDExportResponse(getLocationURI(), statusCode, null, operationOutcome);
    }

    @Override
    public URI getLocationURI() {
        return null;
    }

    @Override
    public BDFailedExportFuture setPollingInterval(Integer millis) {
        return this;
    }
}
