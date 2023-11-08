package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;


/**
 * Bulk data export client.
 *
 * <p>
 * See the <a href="https://hl7.org/fhir/uv/bulkdata/export/index.html#request-flow">FHIR Bulk Data Export Documentation</a>
 * page for more information on how to use this feature.
 * </p>
 */
class HapiFhirExportClient {
    private final Logger logger = LoggerFactory.getLogger(HapiFhirExportClient.class);

    private final FhirContext fhirContext;
    private final HttpClient httpClient;

    public HapiFhirExportClient(FhirContext fhirContext, HttpClient httpClient) {
        this.fhirContext = fhirContext;
        this.httpClient = httpClient;
    }

    /**
     * Initiate an async bulk data export
     */
    public HttpResponse initiate(BDExportRequest exportRequest) throws IOException {
        Parameters parameters = exportRequest.toParameters(fhirContext);
        String body = fhirContext.newJsonParser().encodeResourceToString(parameters);

        logger.info("Initiating a 'Bulk Data Export'");

        HttpPost request = new HttpPost(exportRequest.getExportUri());
        request.setHeader("Prefer", "respond-async");
        request.setHeader("Content-Type", Constants.CT_JSON);

        StringEntity entity = new StringEntity(body, StandardCharsets.UTF_8);
        request.setEntity(entity);

        return httpClient.execute(request);
    }

    /**
     * Poll an initiated Bulk Data Export, given a polling location.
     */
    public HttpResponse poll(URI contentLocation) throws IOException {
        HttpGet request = new HttpGet(contentLocation);

        logger.info("Polling status at '" + contentLocation + "'");

        return httpClient.execute(request);
    }

    /**
     * Cancel a Bulk Data Export, given a polling location.
     */
    public HttpResponse cancel(URI contentLocation) throws IOException {
        HttpDelete request = new HttpDelete(contentLocation);

        logger.info("Cancelling export '" + contentLocation + "'");

        return httpClient.execute(request);
    }
}
