package com.trifork.ehealth.export.future;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trifork.ehealth.export.response.BDExportResponse;
import com.trifork.ehealth.export.response.BDExportResultResponse;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class CompletedExportFuture implements BDExportFuture {
    private final HttpResponse response;
    private final URI locationUri;

    private Logger logger = LoggerFactory.getLogger(CompletedExportFuture.class);

    public CompletedExportFuture(HttpResponse response, URI locationUri) {
        this.response = response;
        this.locationUri = locationUri;

        assert response.getStatusLine().getStatusCode() == 200;
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
        return createResponse();
    }

    @Override
    public BDExportResponse get(long timeout, TimeUnit unit) {
        return createResponse();
    }

    private BDExportResponse createResponse() {
        HttpEntity entity = response.getEntity();
        BDExportResultResponse result = null;

        if (entity != null) {
            try (InputStream content = entity.getContent()) {
                byte[] bytes = content.readAllBytes();

                logger.info("Reading " + bytes.length + " bytes from 'Bulk Data Export'");

                if (bytes.length > 0) {
                    try {
                        result = new ObjectMapper().readValue(bytes, BDExportResultResponse.class);
                    } catch (Exception e) {
                        logger.error("Failed to parse response entity", e);
                        // Empty content, so no results.
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        int statusCode = response.getStatusLine().getStatusCode();
        return new BDExportResponse(getLocationURI(), statusCode, result, null);
    }

    @Override
    public URI getLocationURI() {
        return locationUri;
    }
}
