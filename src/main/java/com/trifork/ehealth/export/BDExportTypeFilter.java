package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IQueryParameterType;
import org.hl7.fhir.r4.model.ResourceType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class BDExportTypeFilter {
    private final ResourceType resourceType;
    private final Map<String, IQueryParameterType> queryParams = new LinkedHashMap<>();

    public BDExportTypeFilter(ResourceType resourceType, String paramName, IQueryParameterType paramQuery) {
        Objects.requireNonNull(resourceType);
        Objects.requireNonNull(paramName);
        Objects.requireNonNull(paramQuery);

        this.resourceType = resourceType;
        queryParams.put(paramName, paramQuery);
    }

    public BDExportTypeFilter and(String paramName, IQueryParameterType paramQuery) {
        queryParams.put(paramName, paramQuery);
        return this;
    }

    public String toTypeFilterString(FhirContext fhirContext) {
        StringBuilder builder = new StringBuilder();
        builder.append(resourceType.name());

        if (!queryParams.isEmpty()) {
            builder.append("?");

            String params = queryParams.entrySet().stream()
                    .map(entry -> {
                        IQueryParameterType value = entry.getValue();
                        return String.format("%s=%s", entry.getKey(), value.getValueAsQueryToken(fhirContext));
                    })
                    .collect(Collectors.joining("&"));
            builder.append(params);
        }

        return builder.toString();
    }

    @Override
    public String toString() {
        return "BDExportTypeFilter{" +
                "resourceType=" + resourceType.name() +
                ", queryParams=" + queryParams +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BDExportTypeFilter that = (BDExportTypeFilter) o;
        return resourceType == that.resourceType && Objects.equals(queryParams, that.queryParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, queryParams);
    }
}
