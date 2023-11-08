package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import ca.uhn.fhir.rest.param.TokenParam;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class TestBDExportTypeFilter {
    private final FhirContext fhirContext = FhirContext.forR4();

    @Test
    void has_valid_token_query_string() {
        BDExportTypeFilter typeFilter
                = new BDExportTypeFilter(
                ResourceType.MedicationRequest,
                "status",
                new TokenParam("active")
        );

        assertEquals("MedicationRequest?status=active", typeFilter.toTypeFilterString(fhirContext));
    }

    @Test
    void has_valid_date_query_string() {
        Date date = new Date(1697799741000L);

        BDExportTypeFilter typeFilter = new BDExportTypeFilter(
                ResourceType.MedicationRequest,
                "date",
                new DateParam(
                        ParamPrefixEnum.GREATERTHAN,
                        new DateType(date, TemporalPrecisionEnum.DAY)
                )
        );

        assertEquals(
                "MedicationRequest?date=gt2023-10-20", typeFilter.toTypeFilterString(fhirContext));
    }

    @Test
    void has_valid_query_string_with_and_operator() {
        BDExportTypeFilter typeFilter
                = new BDExportTypeFilter(
                ResourceType.MedicationRequest,
                "status",
                new TokenParam("active")
        ).and("priority", new TokenParam("routine"));

        assertEquals(
                "MedicationRequest?status=active&priority=routine", typeFilter.toTypeFilterString(fhirContext));
    }

    @Test
    void multiple_param_values_are_comma_separated() {
        Date date = new Date(1697799741000L);

        BDExportTypeFilter typeFilter = new BDExportTypeFilter(
                ResourceType.MedicationRequest,
                "_lastUpdated",
                new DateParam(
                        ParamPrefixEnum.GREATERTHAN,
                        new DateType(date, TemporalPrecisionEnum.DAY)
                )
        ).and(
                "_lastUpdated",
                new DateParam(
                        ParamPrefixEnum.LESSTHAN,
                        new DateType(date, TemporalPrecisionEnum.DAY)
                )
        );

        assertEquals(
                "MedicationRequest?_lastUpdated=gt2023-10-20&_lastUpdated=lt2023-10-20", typeFilter.toTypeFilterString(fhirContext));
    }
}
