package com.trifork.ehealth.export;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Parameters;


public class HapiFhirExportClient {
    private final IGenericClient hapiFhirClient;

    public HapiFhirExportClient(IGenericClient hapiFhirClient) {
        this.hapiFhirClient = hapiFhirClient;
    }

public class BDExportClient<B extends IBaseBinary> {

}
