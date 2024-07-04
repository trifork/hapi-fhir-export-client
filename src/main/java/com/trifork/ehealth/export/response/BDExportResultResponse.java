package com.trifork.ehealth.export.response;

import ca.uhn.fhir.jpa.bulk.export.model.BulkExportResponseJson;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * See <a href="https://hl7.org/fhir/uv/bulkdata/export/index.html#request-flow">Complete Status Documentation</a>
 */
public class BDExportResultResponse implements Serializable {
    private final BulkExportResponseJson response;
    private final List<OutputItem> outputItems;
    private final List<OutputItem> errorItems;

    public BDExportResultResponse(BulkExportResponseJson response) {
        this.response = response;
        this.outputItems = response.getOutput()
                .stream()
                .map(o -> new OutputItem(o.getType(), o.getUrl()))
                .collect(Collectors.toList());
        this.errorItems = response.getError()
                .stream()
                .map(o -> new OutputItem(o.getType(), o.getUrl()))
                .collect(Collectors.toList());
    }

    public static class OutputItem {
        private String type;
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
        public String toString() {
            return "OutputItem{" +
                    "type='" + type + '\'' +
                    ", url='" + url + '\'' +
                    '}';
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

    public Date getTransactionTime() {
        return response.getTransactionTime();
    }

    public String getRequest() {
        return response.getRequest();
    }

    public boolean isRequiresAccessToken() {
        return response.getRequiresAccessToken();
    }

    public List<OutputItem> getOutput() {
        return outputItems;
    }

    public List<OutputItem> getError() {
        return errorItems;
    }

    public String getMessage() {
        return response.getMsg();
    }

    @Override
    public String toString() {
        return "BDExportResultResponse{" +
                "transactionTime=" + getTransactionTime() +
                ", request='" + getRequest() + '\'' +
                ", requiresAccessToken=" + isRequiresAccessToken() +
                ", output=" + getOutput() +
                ", error=" + getError() +
                ", message='" + getMessage() + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BDExportResultResponse that = (BDExportResultResponse) o;
        return isRequiresAccessToken() == that.isRequiresAccessToken()
                && Objects.equals(getTransactionTime(), that.getTransactionTime())
                && Objects.equals(getRequest(), that.getRequest())
                && Objects.equals(getOutput(), that.getOutput())
                && Objects.equals(getError(), that.getError())
                && Objects.equals(getMessage(), that.getMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTransactionTime(), getRequest(), isRequiresAccessToken(), getOutput(), getError(), getMessage());
    }
}
