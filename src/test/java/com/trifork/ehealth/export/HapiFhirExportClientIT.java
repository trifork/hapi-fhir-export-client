package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.codesystems.ConditionClinical;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URI;
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
    private HapiFhirExportClient exportClient;
    private URI baseUri;

    @BeforeAll
    void setup() throws InterruptedException {
        FhirContext fhirContext = FhirContext.forR4();
        HttpClient httpClient = HttpClientBuilder.create().build();
        this.baseUri = URI.create("http://localhost:8080/fhir");
        this.exportClient = new HapiFhirExportClient(fhirContext, httpClient);
        IGenericClient hapiFhirClient = fhirContext.newRestfulGenericClient(baseUri.toString());

        // Create test resources for export
        for (ConditionClinical conditionClinical : ConditionClinical.values()) {
            hapiFhirClient.create().resource(createCondition(conditionClinical)).execute();
        }
    }


    @Test
    void bulk_data_export_is_initiated() throws IOException, InterruptedException {
        HttpResponse response = exportClient.initiate(createExportRequest(baseUri));

        assertEquals(202, response.getStatusLine().getStatusCode());
        assertTrue(response.getHeaders("content-location").length > 0);

        URI contentLocation = extractContentLocation(response);
        assertTrue(contentLocation.toString().contains("_jobId"));
        assertThat(contentLocation.toString()).matches(Pattern.compile(".*\\?_jobId=[a-f0-9-]+$"));
    }

    @Test
    void ongoing_bulk_data_export_can_be_polled() throws IOException, InterruptedException {
        HttpResponse initiateResponse = exportClient.initiate(createExportRequest(baseUri));
        URI contentLocation = extractContentLocation(initiateResponse);

        HttpResponse pollResponse = exportClient.poll(contentLocation);

        assertEquals(202, pollResponse.getStatusLine().getStatusCode());
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void ongoing_bulk_data_export_can_be_cancelled() throws IOException, InterruptedException {
        HttpResponse initiateResponse = exportClient.initiate(createExportRequest(baseUri));
        URI contentLocation = extractContentLocation(initiateResponse);

        HttpResponse cancelResponse = exportClient.cancel(contentLocation);
        assertEquals(202, cancelResponse.getStatusLine().getStatusCode());

        HttpResponse pollResponse;
        do {
            Thread.sleep(10000);
            pollResponse = exportClient.poll(contentLocation);
        } while (!BDExportUtils.isCancelled(pollResponse));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void bulk_data_export_eventually_finishes() throws IOException, InterruptedException {
        HttpResponse initiateResponse = exportClient.initiate(createExportRequest(baseUri));
        URI contentLocation = extractContentLocation(initiateResponse);

        HttpResponse pollResponse = exportClient.poll(contentLocation);
        assertTrue(pollResponse.getHeaders("retry-after").length > 0);
        assertTrue(pollResponse.getHeaders("x-progress").length > 0);

        while (pollResponse.getStatusLine().getStatusCode() == 202) {
            Thread.sleep(60000);

            pollResponse = exportClient.poll(contentLocation);
        }

        assertThat(pollResponse.getStatusLine().getStatusCode()).isEqualTo(200);

        BDExportResultResponse response = new ObjectMapper().readValue(pollResponse.getEntity().getContent(), BDExportResultResponse.class);

        assertTrue(response.getError().isEmpty());
        assertEquals(baseUri.toString() + "$export", response.getRequest());
        assertTrue(response.isRequiresAccessToken());

        List<BDExportResultResponse.OutputItem> output = response.getOutput();
        assertThat(output.size()).isGreaterThan(0);

        BDExportResultResponse.OutputItem outputItem = output.get(0);
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
