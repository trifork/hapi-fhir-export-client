package com.trifork.ehealth.export;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.trifork.ehealth.export.test.HapiFhirTestContainer;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ConditionClinical;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ExportClientIT {
    private HapiFhirTestContainer hapiFhirTestContainer;
    private ExportClient exportClient;
    private final List<Condition> createdBundles = new ArrayList<>();

    @BeforeAll
    void setup() throws InterruptedException {
        hapiFhirTestContainer = new HapiFhirTestContainer();
        hapiFhirTestContainer.start();

        IGenericClient hapiFhirClient = hapiFhirTestContainer.createHapiFhirClient();
        exportClient = new ExportClient(hapiFhirClient);

        // Create test resources for export
        for (ConditionClinical conditionClinical : ConditionClinical.values()) {
            MethodOutcome outcome = hapiFhirClient.create().resource(createCondition(conditionClinical)).execute();
            createdBundles.add((Condition) outcome.getResource());
        }
    }

    @AfterAll
    void tearDown() {
        hapiFhirTestContainer.stop();
    }

    @Test
    void bulk_data_export_is_initiated() {
        Parameters parameters = createExportParameters();
        MethodOutcome outcome = exportClient.initiate(parameters);

        assertEquals(202, outcome.getResponseStatusCode());
        assertTrue(outcome.getResponseHeaders().containsKey("content-location"));

        String contentLocation = getContentLocation(outcome);
        assertThat(contentLocation).matches(Pattern.compile("^.*\\/fhir\\/\\$export-poll-status\\?_jobId=([a-f0-9-]+)$"));
    }


    private static String getContentLocation(MethodOutcome outcome) {
        return outcome.getResponseHeaders().get("content-location").get(0);
    }

    private static Parameters createExportParameters() {
        return new Parameters()
                .addParameter("_outputFormat", Constants.CT_FHIR_NDJSON)
                .addParameter("_type", ResourceType.Condition.name());
    }

    private static Condition createCondition(ConditionClinical conditionClinical) {
        return new Condition()
                .setClinicalStatus(
                        new CodeableConcept(
                                new Coding(conditionClinical.getSystem(), conditionClinical.toCode(), conditionClinical.getDisplay())
                        )
                );
    }
}
