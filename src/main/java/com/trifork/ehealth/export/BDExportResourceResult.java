package com.trifork.ehealth.export;

import org.hl7.fhir.r4.model.Binary;

import java.util.List;
import java.util.Map;

public class BDExportResourceResult {
    private String transactionTime;
    private String request;
    private boolean requiresAccessToken;
    private List<ResourceItem> output;
    private List<ResourceItem> error;
    private Map<String, Object> extension;

    BDExportResourceResult(
            String transactionTime,
            String request,
            boolean requiresAccessToken,
            List<ResourceItem> output,
            List<ResourceItem> error,
            Map<String, Object> extension
    ) {
        this.transactionTime = transactionTime;
        this.request = request;
        this.requiresAccessToken = requiresAccessToken;
        this.output = output;
        this.error = error;
        this.extension = extension;
    }

    public String getTransactionTime() {
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

    public Map<String, Object> getExtension() {
        return extension;
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
