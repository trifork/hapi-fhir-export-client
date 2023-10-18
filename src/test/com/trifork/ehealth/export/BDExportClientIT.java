package com.trifork.ehealth.export;

import com.trifork.ehealth.export.test.HapiFhirTestContainer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class BDExportClientIT {
    @Test
    void test() {
        HapiFhirTestContainer hapiFhirTestContainer = new HapiFhirTestContainer();
        hapiFhirTestContainer.start();

        System.out.println(hapiFhirTestContainer.getMappedPort());

        hapiFhirTestContainer.stop();
    }
}
