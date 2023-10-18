package com.trifork.ehealth.export;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.trifork.ehealth.export.test.HapiFhirTestContainer;
import org.junit.jupiter.api.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class HapiFhirExportClientIT {
    private static HapiFhirTestContainer hapiFhirTestContainer;
    private static IGenericClient hapiFhirClient;

    @BeforeAll
    static void setup() {
        if (hapiFhirTestContainer == null) {
            hapiFhirTestContainer = new HapiFhirTestContainer();
            hapiFhirTestContainer.start();
        }

        hapiFhirClient = hapiFhirTestContainer.createHapiFhirClient();
    }

    @AfterAll
    static void tearDown() {
        if (hapiFhirTestContainer != null) {
            hapiFhirTestContainer.stop();
            hapiFhirTestContainer = null;
        }
    }

    @Test
    void test_container_setup() {
        hapiFhirClient.capabilities();
    }
}
