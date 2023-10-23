package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IHttpClient;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import org.hl7.fhir.r4.model.Parameters;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * Bulk data export client.
 *
 * <p>
 * See the <a href="https://hl7.org/fhir/uv/bulkdata/export/index.html#request-flow">FHIR Bulk Data Export Documentation</a>
 * page for more information on how to use this feature.
 * </p>
 */
public class BDExportClient {
    private final FhirContext fhirContext;
    private final URI baseUri;
    private final HttpClient httpClient;

    public BDExportClient(FhirContext fhirContext, URI baseUri, HttpClient httpClient) {
        this.fhirContext = fhirContext;
        this.baseUri = baseUri;
        this.httpClient = httpClient;
    }

    /**
     * Initiate an async bulk data export
     */
    public HttpResponse<String> initiate(BDExportRequest exportRequest) throws IOException, InterruptedException {
        Parameters parameters = exportRequest.toParameters(fhirContext);
        String body = fhirContext.newJsonParser().encodeResourceToString(parameters);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(exportRequest.getUri())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .header("Prefer", "respond-async")
                .header("Content-Type", Constants.CT_JSON)
                .build();

        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Poll an initiated Bulk Data Export.
     */
    public MethodOutcome poll(URI contentLocation) throws IOException {
        /**
         * We are unable to parse the polling request using the standard client, since it requires a "resourceType" param
         * in the response body, which the bulk-data-export API does not do.
         * Therefore we need to use the internal one, to construct our own raw HTTP request.
         */
        /*IHttpClient httpClient = hapiFhirClient.getHttpClient();
        IHttpRequest request = httpClient.createParamRequest(fhirContext, new HashMap<>(), EncodingEnum.JSON);
        request.setUri(contentLocation);
        request.addHeader("Content-Type", Constants.CT_JSON);

        IHttpResponse response = request.execute();*/

        MethodOutcome methodOutcome = new MethodOutcome();
        methodOutcome.setResponseStatusCode(202);
        methodOutcome.setResponseHeaders(Map.of("retry-after", Collections.singletonList("20")));
        return methodOutcome;
    }
}
