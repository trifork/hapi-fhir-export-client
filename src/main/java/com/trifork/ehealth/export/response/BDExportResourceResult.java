package com.trifork.ehealth.export.response;

import org.hl7.fhir.r4.model.Binary;

import java.util.Date;
import java.util.List;

public class BDExportResourceResult {
    private Date transactionTime;
    private String request;
    private boolean requiresAccessToken;
    private List<ResourceItem> output;
    private List<ResourceItem> error;
    private String message;

    public BDExportResourceResult(
            Date transactionTime,
            String request,
            boolean requiresAccessToken,
            List<ResourceItem> output,
            List<ResourceItem> error,
            String message
    ) {
        this.transactionTime = transactionTime;
        this.request = request;
        this.requiresAccessToken = requiresAccessToken;
        this.output = output;
        this.error = error;
        this.message = message;
    }

    public Date getTransactionTime() {
        return transactionTime;
    }

    public String getRequest() {
        return request;
    }

    public boolean isRequiresAccessToken() {
        return requiresAccessToken;
    }

    public List<ResourceItem> getOutput() {
        return output;
    }

    public List<ResourceItem> getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public static class ResourceItem {
        private final String type;
        private final Binary resource;

        public ResourceItem(String type, Binary resource) {
            this.type = type;
            this.resource = resource;
        }

        public String getType() {
            return type;
        }

        public Binary getResource() {
            return resource;
        }
    }
}
