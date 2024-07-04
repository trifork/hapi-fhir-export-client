package com.trifork.ehealth.export.response;

import org.hl7.fhir.r4.model.OperationOutcome;

import java.net.URI;
import java.util.Optional;

public class BDExportResponse {
    private final URI contentLocation;
    private final int statusCode;
    private final BDExportResultResponse result;
    private final OperationOutcome error;

    public BDExportResponse(URI contentLocation, int statusCode, BDExportResultResponse result, OperationOutcome error) {
        this.contentLocation = contentLocation;
        this.statusCode = statusCode;
        this.result = result;
        this.error = error;
    }

    public URI getContentLocation() {
        return contentLocation;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Optional<BDExportResultResponse> getResult() {
        return Optional.ofNullable(result);
    }

    public Optional<OperationOutcome> getError() {
        return Optional.ofNullable(error);
    }
}
