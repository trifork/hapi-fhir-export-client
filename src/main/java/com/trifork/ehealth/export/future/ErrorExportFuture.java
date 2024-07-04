package com.trifork.ehealth.export.future;

import ca.uhn.fhir.context.FhirContext;
import com.trifork.ehealth.export.response.BDExportResponse;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class ErrorExportFuture implements BDExportFuture {
    private final FhirContext fhirContext;
    private final HttpResponse response;
    private final URI locationUri;

    public ErrorExportFuture(FhirContext fhirContext, HttpResponse response, URI locationUri) {
        this.fhirContext = fhirContext;
        this.response = response;
        this.locationUri = locationUri;

        int statusCode = response.getStatusLine().getStatusCode();
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

        HttpEntity entity = response.getEntity();

        if (entity != null) {
            try(InputStream content = entity.getContent()) {
                operationOutcome = fhirContext.newJsonParser().parseResource(OperationOutcome.class, content);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        int statusCode = response.getStatusLine().getStatusCode();
        return new BDExportResponse(getLocationURI(), statusCode, null, operationOutcome);
    }

    @Override
    public URI getLocationURI() {
        return locationUri;
    }
}
