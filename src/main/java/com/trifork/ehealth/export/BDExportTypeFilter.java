package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IQueryParameterType;
import org.hl7.fhir.r4.model.ResourceType;

import java.util.*;
import java.util.stream.Collectors;

public class BDExportTypeFilter {
    private final ResourceType resourceType;
    private final List<Query> queries = new ArrayList<>();

    public BDExportTypeFilter(ResourceType resourceType, String paramName, IQueryParameterType paramQuery) {
        Objects.requireNonNull(resourceType);
        Objects.requireNonNull(paramName);
        Objects.requireNonNull(paramQuery);

        this.resourceType = resourceType;
        queries.add(new Query(paramName, paramQuery));
    }

    public BDExportTypeFilter and(String paramName, IQueryParameterType paramQuery) {
        queries.add(new Query(paramName, paramQuery));
        return this;
    }

    public String toTypeFilterString(FhirContext fhirContext) {
        StringBuilder builder = new StringBuilder();
        builder.append(resourceType.name());

        if (!queries.isEmpty()) {
            builder.append("?");

            String params = queries.stream()
                    .map(q -> String.format("%s=%s", q.getParamName(), q.getParamQuery().getValueAsQueryToken(fhirContext)))
                    .collect(Collectors.joining("&"));
            builder.append(params);
        }

        return builder.toString();
    }

    static class Query {
        private String paramName;
        private IQueryParameterType paramQuery;

        public Query(String key, IQueryParameterType query) {
            this.paramName = key;
            this.paramQuery = query;
        }

        public String getParamName() {
            return paramName;
        }

        public IQueryParameterType getParamQuery() {
            return paramQuery;
        }
    }

    @Override
    public String toString() {
        return "BDExportTypeFilter{" +
                "resourceType=" + resourceType.name() +
                ", queryParams=" + queries +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BDExportTypeFilter that = (BDExportTypeFilter) o;
        return resourceType == that.resourceType && Objects.equals(queries, that.queries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, queries);
    }
}
