package com.trifork.ehealth.export;

import org.hl7.fhir.r4.model.OperationOutcome;

import java.util.Optional;

public class BDExportResponse {
    private final int statusCode;
    private final BDExportResult result;
    private final OperationOutcome error;

    public BDExportResponse(int statusCode, BDExportResult result, OperationOutcome error) {
        this.statusCode = statusCode;
        this.result = result;
        this.error = error;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Optional<BDExportResult> getResult() {
        return Optional.ofNullable(result);
    }

    public Optional<OperationOutcome> getError() {
        return Optional.ofNullable(error);
    }
}
