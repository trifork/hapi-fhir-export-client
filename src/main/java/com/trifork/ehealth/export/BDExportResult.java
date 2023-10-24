package com.trifork.ehealth.export;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * See <a href="https://hl7.org/fhir/uv/bulkdata/export/index.html#request-flow">Complete Status Documentation</a>
 */
public class BDExportResult {
    @JsonProperty("transactionTime")
    private String transactionTime;

    @JsonProperty("request")
    private String request;

    @JsonProperty("requiresAccessToken")
    private boolean requiresAccessToken;

    @JsonProperty("output")
    private List<OutputItem> output;

    @JsonProperty("error")
    private List<OutputItem> error;

    @JsonProperty("extension")
    private Map<String, Object> extension;

    public static class OutputItem {
        @JsonProperty("type")
        private String type;

        @JsonProperty("url")
        private String url;

        public String getType() {
            return type;
        }

        public String getUrl() {
            return url;
        }
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

    public List<OutputItem> getOutput() {
        return output;
    }

    public List<OutputItem> getError() {
        return error;
    }

    public Map<String, Object> getExtension() {
        return extension;
    }
}
