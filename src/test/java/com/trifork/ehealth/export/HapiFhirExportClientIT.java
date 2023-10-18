package com.trifork.ehealth.export;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.trifork.ehealth.export.test.HapiFhirTestContainer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class HapiFhirExportClientIT {
    private static HapiFhirTestContainer hapiFhirTestContainer;
    private static IGenericClient hapiFhirClient;

    private Logger logger = LoggerFactory.getLogger(HapiFhirExportClientIT.class);

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
        logger.info("TEST");

        //hapiFhirClient.capabilities();
    }
}
