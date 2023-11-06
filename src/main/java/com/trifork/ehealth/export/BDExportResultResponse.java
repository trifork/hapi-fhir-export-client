package com.trifork.ehealth.export;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * See <a href="https://hl7.org/fhir/uv/bulkdata/export/index.html#request-flow">Complete Status Documentation</a>
 */
public class BDExportResultResponse implements Serializable {
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

    public BDExportResultResponse() {
    }

    public BDExportResultResponse(
            String transactionTime,
            String request,
            boolean requiresAccessToken,
            List<OutputItem> output,
            List<OutputItem> error,
            Map<String, Object> extension
    ) {
        this.transactionTime = transactionTime;
        this.request = request;
        this.requiresAccessToken = requiresAccessToken;
        this.output = output;
        this.error = error;
        this.extension = extension;
    }

    public static class OutputItem {
        @JsonProperty("type")
        private String type;

        @JsonProperty("url")
        private String url;

        public OutputItem() {
        }

        @JsonIgnore
        public OutputItem(String type, String url) {
            this.type = type;
            this.url = url;
        }

        public String getType() {
            return type;
        }

        public String getUrl() {
            return url;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OutputItem that = (OutputItem) o;
            return Objects.equals(type, that.type) && Objects.equals(url, that.url);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, url);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BDExportResultResponse that = (BDExportResultResponse) o;
        return requiresAccessToken == that.requiresAccessToken
                && Objects.equals(transactionTime, that.transactionTime)
                && Objects.equals(request, that.request)
                && Objects.equals(output, that.output)
                && Objects.equals(error, that.error)
                && Objects.equals(extension, that.extension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionTime, request, requiresAccessToken, output, error, extension);
    }
}
