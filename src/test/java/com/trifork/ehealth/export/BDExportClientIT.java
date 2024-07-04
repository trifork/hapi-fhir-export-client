package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.trifork.ehealth.export.future.BDExportFuture;
import com.trifork.ehealth.export.response.BDExportResourceResult;
import com.trifork.ehealth.export.response.BDExportResponse;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ConditionClinical;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BDExportClientIT {
    private URI baseUri;
    private final List<Condition> createdResources = new ArrayList<>();
    private BDExportClient exportClient;
    private BDExportConverter exportResourceConverter;
    private HttpClient httpClient;
    private FhirContext fhirContext;

    @BeforeAll
    void setup() {
        this.fhirContext = FhirContext.forR4();
        this.httpClient = HttpClientBuilder.create().build();
        this.baseUri = URI.create("http://localhost:8080/fhir");
        IGenericClient hapiFhirClient = fhirContext.newRestfulGenericClient(baseUri.toString());
        this.exportClient = new BDExportClient(fhirContext, httpClient);
        this.exportResourceConverter = new BDExportConverter(hapiFhirClient);

        // Create test resources for export
        for (ConditionClinical conditionClinical : List.of(ConditionClinical.ACTIVE, ConditionClinical.INACTIVE, ConditionClinical.RECURRENCE)) {
            MethodOutcome outcome = hapiFhirClient.create().resource(createCondition(conditionClinical)).execute();
            createdResources.add((Condition) outcome.getResource());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void bulk_data_export_is_successful() throws IOException, InterruptedException, ExecutionException {
        Future<BDExportResponse> future = exportClient.initiate(createExportRequest(baseUri));

        assertFalse(future.isCancelled());
        assertFalse(future.isDone());

        BDExportResponse response = future.get();

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getError().isEmpty());
        assertTrue(response.getResult().isPresent());
        assertFalse(future.isCancelled());
        assertTrue(future.isDone());

        // Now test the mapping...
        BDExportResourceResult resourceResult = exportResourceConverter.convert(response.getResult().get());

        List<BDExportResourceResult.ResourceItem> outputResources = resourceResult.getOutput();
        assertEquals(1, outputResources.size());

        BDExportResourceResult.ResourceItem resourceItem = outputResources.get(0);
        assertEquals(ResourceType.Condition.name(), resourceItem.getType());

        Binary resource = resourceItem.getResource();
        assertEquals(Constants.CT_FHIR_NDJSON, resource.getContentType());

        String content = new String(resource.getData(), StandardCharsets.UTF_8);
        for (Condition condition : createdResources) {
            assertThat(content).contains(condition.getClinicalStatus().getCoding().get(0).getCode());
        }
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void ongoing_bulk_data_export_can_be_cancelled() throws IOException {
        BDExportFuture future = exportClient.initiate(createExportRequest(baseUri));

        assertFalse(future.isCancelled());

        future.cancel(true);

        assertTrue(future.isCancelled());

        assertThrows(InterruptedException.class, future::get);

        // Wait until it is truely cancelled, so we don't break the next test.
        // Apparently initiating a new one, while cancelling of the old is in progress, this causes the old to be reused.
        HttpResponse response = exportClient.poll(future.getLocationURI());

        while (!BDExportUtils.isCancelled(response)) {
            try {
                Thread.sleep(1000);
                response = exportClient.poll(future.getLocationURI());
            } catch (InterruptedException e) {}
        }

    }

    @Test
    void ongoing_bulk_data_export_can_be_polled() throws IOException {
        BDExportFuture future = exportClient.initiate(createExportRequest(baseUri));
        HttpResponse pollResponse = exportClient.poll(future.getLocationURI());

        assertEquals(202, pollResponse.getStatusLine().getStatusCode());
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
