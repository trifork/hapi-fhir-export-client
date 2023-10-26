package com.trifork.ehealth.export;

import org.hl7.fhir.r4.model.OperationOutcome;

import java.util.Optional;

public class BDExportResponse {
    private final int statusCode;
    private final BDExportCompleteResult result;
    private final OperationOutcome error;

    BDExportResponse(int statusCode, BDExportCompleteResult result, OperationOutcome error) {
        this.statusCode = statusCode;
        this.result = result;
        this.error = error;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Optional<BDExportCompleteResult> getResult() {
        return Optional.ofNullable(result);
    }

    public Optional<OperationOutcome> getError() {
        return Optional.ofNullable(error);
    }
}
