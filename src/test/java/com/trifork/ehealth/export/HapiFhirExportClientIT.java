package com.trifork.ehealth.export;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.trifork.ehealth.export.test.HapiFhirTestContainer;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.codesystems.ConditionClinical;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HapiFhirExportClientIT {
    private HapiFhirTestContainer hapiFhirTestContainer;
    private HapiFhirExportClient hapiFhirExportClient;
    private final List<Condition> createdBundles = new ArrayList<>();

    @BeforeAll
    void setup() {
        hapiFhirTestContainer = new HapiFhirTestContainer();
        hapiFhirTestContainer.start();

        IGenericClient hapiFhirClient = hapiFhirTestContainer.createHapiFhirClient();
        hapiFhirExportClient = new HapiFhirExportClient(hapiFhirClient);

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
