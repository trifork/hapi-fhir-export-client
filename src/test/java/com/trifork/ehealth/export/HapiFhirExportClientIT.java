package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trifork.ehealth.export.test.HapiFhirTestContainer;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.codesystems.ConditionClinical;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.trifork.ehealth.export.BDExportUtils.extractContentLocation;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HapiFhirExportClientIT {
    private HapiFhirTestContainer hapiFhirTestContainer;
    private HapiFhirExportClient exportClient;
    private URI baseUri;

    @BeforeAll
    void setup() throws InterruptedException {
        hapiFhirTestContainer = new HapiFhirTestContainer();
        hapiFhirTestContainer.start();

        FhirContext fhirContext = FhirContext.forR4();
        HttpClient httpClient = HttpClient.newHttpClient();
        this.baseUri = hapiFhirTestContainer.getHapiFhirUri();
        this.exportClient = new HapiFhirExportClient(fhirContext, httpClient);
        IGenericClient hapiFhirClient = fhirContext.newRestfulGenericClient(baseUri.toString());

        // Create test resources for export
        for (ConditionClinical conditionClinical : ConditionClinical.values()) {
            hapiFhirClient.create().resource(createCondition(conditionClinical)).execute();
        }
    }

    @AfterAll
    void tearDown() {
        hapiFhirTestContainer.stop();
    }

    @Test
    void bulk_data_export_is_initiated() throws IOException, InterruptedException {
        HttpResponse<String> response = exportClient.initiate(createExportRequest(baseUri));

        assertEquals(202, response.statusCode());
        assertTrue(response.headers().firstValue("content-location").isPresent());

        URI contentLocation = extractContentLocation(response);
        assertTrue(contentLocation.toString().contains("_jobId"));
        assertThat(contentLocation.toString()).matches(Pattern.compile(".*\\?_jobId=[a-f0-9-]+$"));
    }

    @Test
    void ongoing_bulk_data_export_can_be_polled() throws IOException, InterruptedException {
        HttpResponse<String> initiateResponse = exportClient.initiate(createExportRequest(baseUri));
        URI contentLocation = extractContentLocation(initiateResponse);

        HttpResponse<String> pollResponse = exportClient.poll(contentLocation);

        assertEquals(202, pollResponse.statusCode());
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void ongoing_bulk_data_export_can_be_cancelled() throws IOException, InterruptedException {
        HttpResponse<String> initiateResponse = exportClient.initiate(createExportRequest(baseUri));
        URI contentLocation = extractContentLocation(initiateResponse);

        HttpResponse<String> cancelResponse = exportClient.cancel(contentLocation);
        assertEquals(202, cancelResponse.statusCode());

        HttpResponse<String> pollResponse;
        do {
            Thread.sleep(10000);
            pollResponse = exportClient.poll(contentLocation);
        } while (!BDExportUtils.isCancelled(pollResponse.headers()));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void bulk_data_export_eventually_finishes() throws IOException, InterruptedException {
        HttpResponse<String> initiateResponse = exportClient.initiate(createExportRequest(baseUri));
        URI contentLocation = extractContentLocation(initiateResponse);

        HttpResponse<String> pollResponse = exportClient.poll(contentLocation);
        HttpHeaders headers = pollResponse.headers();
        assertTrue(headers.firstValue("retry-after").isPresent());
        assertTrue(headers.firstValue("x-progress").isPresent());

        while (pollResponse.statusCode() == 202) {
            Thread.sleep(60000);

            pollResponse = exportClient.poll(contentLocation);
        }

        assertThat(pollResponse.statusCode()).isEqualTo(200);

        BDExportCompleteResult response = new ObjectMapper().readValue(pollResponse.body(), BDExportCompleteResult.class);

        assertTrue(response.getError().isEmpty());
        assertEquals(baseUri.toString() + "$export", response.getRequest());
        assertTrue(response.isRequiresAccessToken());

        List<BDExportCompleteResult.OutputItem> output = response.getOutput();
        assertThat(output.size()).isGreaterThan(0);

        BDExportCompleteResult.OutputItem outputItem = output.get(0);
        assertEquals("Condition", outputItem.getType());
        assertThat(outputItem.getUrl()).contains("/fhir/Binary/");
    }

    public static BDExportRequest createExportRequest(URI baseUri) {
        return BDExportRequest.createSystemExportRequest(baseUri).addType(ResourceType.Condition);
    }

    public static Condition createCondition(ConditionClinical conditionClinical) {
        return new Condition()
                .setClinicalStatus(
                        new CodeableConcept(
                                new Coding(conditionClinical.getSystem(), conditionClinical.toCode(), conditionClinical.getDisplay())
                        )
                );
    }
}
