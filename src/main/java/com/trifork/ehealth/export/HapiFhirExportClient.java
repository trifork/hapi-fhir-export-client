package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
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
import java.util.Collections;
import java.util.List;


/**
 * Bulk data export client.
 *
 * <p>
 * See the <a href="https://hl7.org/fhir/uv/bulkdata/export/index.html#request-flow">FHIR Bulk Data Export Documentation</a>
 * page for more information on how to use this feature.
 * </p>
 */
public class HapiFhirExportClient {
    private final Logger logger = LoggerFactory.getLogger(HapiFhirExportClient.class);

    private final FhirContext fhirContext;
    private final HttpClient httpClient;
    private final List<HttpRequestInterceptor> interceptors;

    public HapiFhirExportClient(FhirContext fhirContext, HttpClient httpClient) {
        this(fhirContext, httpClient, Collections.emptyList());
    }

    public HapiFhirExportClient(
            FhirContext fhirContext,
            HttpClient httpClient,
            List<HttpRequestInterceptor> interceptors
    ) {
        this.fhirContext = fhirContext;
        this.httpClient = httpClient;
        this.interceptors = interceptors;
    }

    /**
     * Initiate an async bulk data export
     */
    public HttpResponse initiate(BDExportRequest exportRequest) throws IOException {
        Parameters parameters = exportRequest.toParameters(fhirContext);
        String body = fhirContext.newJsonParser().encodeResourceToString(parameters);

        URI exportUri = exportRequest.getExportUri();
        logger.info("Initiating a 'Bulk Data Export' at: " + exportUri);

        HttpPost request = new HttpPost(exportUri);
        request.setHeader("Prefer", "respond-async");
        request.setHeader("Content-Type", Constants.CT_JSON);

        StringEntity entity = new StringEntity(body, StandardCharsets.UTF_8);
        request.setEntity(entity);

        processInterceptors(request);

        return httpClient.execute(request);
    }

    /**
     * Poll an initiated Bulk Data Export, given a polling location.
     */
    public HttpResponse poll(URI contentLocation) throws IOException {
        HttpGet request = new HttpGet(contentLocation);

        logger.info("Polling status at '" + contentLocation + "'");

        processInterceptors(request);

        return httpClient.execute(request);
    }

    /**
     * Cancel a Bulk Data Export, given a polling location.
     */
    public HttpResponse cancel(URI contentLocation) throws IOException {
        HttpDelete request = new HttpDelete(contentLocation);

        logger.info("Cancelling export '" + contentLocation + "'");

        processInterceptors(request);

        return httpClient.execute(request);
    }

    private void processInterceptors(HttpRequest request) {
        for (HttpRequestInterceptor interceptor : interceptors) {
            try {
                interceptor.process(request, null);
            } catch (HttpException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
