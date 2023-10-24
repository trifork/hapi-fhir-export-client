package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import org.hl7.fhir.r4.model.Parameters;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;


/**
 * Bulk data export client.
 *
 * <p>
 * See the <a href="https://hl7.org/fhir/uv/bulkdata/export/index.html#request-flow">FHIR Bulk Data Export Documentation</a>
 * page for more information on how to use this feature.
 * </p>
 */
public class HapiFhirExportClient {
    private final FhirContext fhirContext;
    private final HttpClient httpClient;

    public HapiFhirExportClient(FhirContext fhirContext, HttpClient httpClient) {
        this.fhirContext = fhirContext;
        this.httpClient = httpClient;
    }

    /**
     * Initiate an async bulk data export
     */
    public HttpResponse<String> initiate(BDExportRequest exportRequest) throws IOException, InterruptedException {
        Parameters parameters = exportRequest.toParameters(fhirContext);
        String body = fhirContext.newJsonParser().encodeResourceToString(parameters);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(exportRequest.getExportUri())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .header("Prefer", "respond-async")
                .header("Content-Type", Constants.CT_JSON)
                .build();

        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Poll an initiated Bulk Data Export, given a polling location.
     */
    public HttpResponse<String> poll(URI contentLocation) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(contentLocation)
                .build();

        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Cancel a Bulk Data Export, using the polling location.
     */
    public HttpResponse<String> cancel(URI contentLocation) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .DELETE()
                .uri(contentLocation)
                .build();

        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
