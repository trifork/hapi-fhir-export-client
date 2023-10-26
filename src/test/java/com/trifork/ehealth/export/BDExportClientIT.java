package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.trifork.ehealth.export.test.HapiFhirTestContainer;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.codesystems.ConditionClinical;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.trifork.ehealth.export.HapiFhirExportClientIT.createCondition;
import static com.trifork.ehealth.export.HapiFhirExportClientIT.createExportRequest;
import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BDExportClientIT {
    private URI baseUri;
    private final List<Condition> createdBundles = new ArrayList<>();
    private BDExportClient exportClient;
    private HttpClient httpClient;

    @BeforeAll
    void setup() throws InterruptedException {
        HapiFhirTestContainer hapiFhirTestContainer = new HapiFhirTestContainer();
        hapiFhirTestContainer.start();

        FhirContext fhirContext = FhirContext.forR4();
        httpClient = HttpClient.newHttpClient();
        this.baseUri = hapiFhirTestContainer.getHapiFhirUri();
        IGenericClient hapiFhirClient = fhirContext.newRestfulGenericClient(baseUri.toString());
        exportClient = new BDExportClient(fhirContext, httpClient);


        // Create test resources for export
        for (ConditionClinical conditionClinical : ConditionClinical.values()) {
            MethodOutcome outcome = hapiFhirClient.create().resource(createCondition(conditionClinical)).execute();
            createdBundles.add((Condition) outcome.getResource());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void bulk_data_export_is_successful() throws IOException, InterruptedException, ExecutionException {
        Future<BDExportResponse> future = exportClient.bulkDataExport(createExportRequest(baseUri));

        assertFalse(future.isCancelled());
        assertFalse(future.isDone());

        BDExportResponse response = future.get();

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getError().isEmpty());
        assertTrue(response.getResult().isPresent());
        assertFalse(future.isCancelled());
        assertTrue(future.isDone());
    }
}
