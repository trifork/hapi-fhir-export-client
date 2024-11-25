package com.trifork.ehealth.export.response;

import static ca.uhn.fhir.rest.api.Constants.STATUS_HTTP_202_ACCEPTED;

public class BDCancelResponse {
    private int statusCode;

    public BDCancelResponse(int statusCode) {
        this.statusCode = statusCode;
    }

    public boolean isAccepted() {
        return statusCode == STATUS_HTTP_202_ACCEPTED;
    }
}
