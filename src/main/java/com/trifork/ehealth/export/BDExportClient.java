package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Parameters;


public class BDExportClient {
    private final FhirContext fhirContext;
    private final IGenericClient hapiFhirClient;

    public BDExportClient(FhirContext fhirContext, IGenericClient hapiFhirClient) {
        this.fhirContext = fhirContext;
        this.hapiFhirClient = hapiFhirClient;
    }

    /**
     * Initiate an async bulk data export
     *
     * <p>
     * See the <a href="https://hl7.org/fhir/uv/bulkdata/export/index.html#request-flow">FHIR Bulk Data Export Documentation</a>
     * page for more information on how to use this feature.
     * </p>
     */
    public MethodOutcome initiate(BDExportRequest request) {
        Parameters parameters = request.toParameters(fhirContext);

        return hapiFhirClient.operation()
                .onServer()
                .named("export")
                .withParameters(parameters)
                .withAdditionalHeader("Prefer", "respond-async")
                .returnMethodOutcome()
                .execute();
    }
}
