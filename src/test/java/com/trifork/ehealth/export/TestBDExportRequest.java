package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.param.TokenParam;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBDExportRequest {
    @Test
    void is_mapped_to_parameters() {
        BDExportTypeFilter typeFilter1 = new BDExportTypeFilter(
                ResourceType.Condition,
                "clinicalStatus",
                new TokenParam("active")
        );
        BDExportTypeFilter typeFilter2 = new BDExportTypeFilter(
                ResourceType.Condition,
                "category",
                new TokenParam("problem-list-item")
        );

        Date date_2023_10_20 = new Date(1697799741000L);
        Parameters parameters = new BDExportRequest()
                .setOutputFormat(Constants.CT_APP_NDJSON)
                .setSince(new InstantType(date_2023_10_20, TemporalPrecisionEnum.DAY))
                .addType(ResourceType.Condition)
                .addType(ResourceType.MedicationRequest)
                .addTypeFilter(typeFilter1)
                .addTypeFilter(typeFilter2)
                .toParameters(FhirContext.forR4());

        assertEquals(Constants.CT_APP_NDJSON, parameters.getParameterValue("_outputFormat").primitiveValue());
        assertEquals("2023-10-20", parameters.getParameterValue("_since").primitiveValue());
        assertEquals("Condition,MedicationRequest", parameters.getParameterValue("_type").primitiveValue());
        assertEquals(
                "Condition?clinicalStatus=active,Condition?category=problem-list-item",
                parameters.getParameterValue("_typeFilter").primitiveValue()
        );
    }
}
