package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ResourceType;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Request model for initiation which follows the specification
 * for <a href="https://hl7.org/fhir/uv/bulkdata/export/index.html#query-parameters">Bulk Data Export - Query Parameters</a>
 */
public class BDExportRequest {
    private final URI exportUri;
    private String outputFormat = Constants.CT_FHIR_NDJSON;
    private InstantType since;
    private final List<ResourceType> types = new ArrayList<>();
    private final List<BDExportTypeFilter> typeFilters = new ArrayList<>();

    BDExportRequest(URI exportUri) {
        this.exportUri = exportUri;
    }

    public BDExportRequest setOutputFormat(String outputFormat) {
        Objects.requireNonNull(outputFormat);

        this.outputFormat = outputFormat;
        return this;
    }

    public BDExportRequest setSince(InstantType since) {
        Objects.requireNonNull(since);

        this.since = since;
        return this;
    }

    public BDExportRequest addType(ResourceType resourceType) {
        Objects.requireNonNull(resourceType);

        types.add(resourceType);
        return this;
    }

    public BDExportRequest addTypeFilter(BDExportTypeFilter typeFilter) {
        Objects.requireNonNull(typeFilter);

        typeFilters.add(typeFilter);
        return this;
    }

    public static BDExportRequest createPatientExportRequest(URI baseFhirUri) {
        return new BDExportRequest(baseFhirUri.resolve("./Patient/$export"));
    }

    public static BDExportRequest createGroupExportRequest(URI baseFhirUri, int groupId) {
        String str = String.format("./Group/%d/$export", groupId);

        return new BDExportRequest(baseFhirUri.resolve(str));
    }

    public static BDExportRequest createSystemExportRequest(URI baseFhirUri) {
        return new BDExportRequest(baseFhirUri.resolve("./$export"));
    }

    public Parameters toParameters(FhirContext fhirContext) {
        Parameters parameters = new Parameters().addParameter("_outputFormat", outputFormat);

        if (since != null) {
            parameters.addParameter("_since", since);
        }

        if (!types.isEmpty()) {
            String typeString = types.stream().map(ResourceType::name)
                    .collect(Collectors.joining(","));

            parameters.addParameter("_type", typeString);
        }

        if (!typeFilters.isEmpty()) {
            String typeFilterString = typeFilters.stream()
                    .map(typeFilter -> typeFilter.toTypeFilterString(fhirContext))
                    .collect(Collectors.joining(","));


            parameters.addParameter("_typeFilter", typeFilterString);
        }

        return parameters;
    }

    public URI getExportUri() {
        return exportUri;
    }
}
