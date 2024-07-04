package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import com.trifork.ehealth.export.future.BDExportFuture;
import com.trifork.ehealth.export.future.CompletedExportFuture;
import com.trifork.ehealth.export.future.ErrorExportFuture;
import com.trifork.ehealth.export.future.OngoingExportFuture;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.BasicHttpContext;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ca.uhn.fhir.rest.api.Constants.STATUS_HTTP_200_OK;
import static ca.uhn.fhir.rest.api.Constants.STATUS_HTTP_202_ACCEPTED;

public class BDExportClient {
    private final FhirContext fhirContext;
    private final HttpClient httpClient;

    private final List<HttpRequestInterceptor> interceptors = new ArrayList<>();

    private static final Logger logger = LoggerFactory.getLogger(BDExportClient.class);

    public BDExportClient(FhirContext fhirContext, HttpClient httpClient) {
        this.fhirContext = fhirContext;
        this.httpClient = httpClient;
    }

    /**
     * Initiate an async bulk data export
     *
     * @param request request parameters
     * @return An export future to poll on
     * @throws IOException
     */
    public BDExportFuture initiate(BDExportRequest request) throws IOException {
        Parameters parameters = request.toParameters(fhirContext);
        String body = fhirContext.newJsonParser().encodeResourceToString(parameters);
        URI exportUri = request.getExportUri();

        HttpPost httpRequest = buildHttpRequest(exportUri, body);

        logger.info("Initiating a 'Bulk Data Export' at: " + exportUri);
        HttpResponse response = httpClient.execute(httpRequest);

        URI contentLocation = BDExportUtils.extractContentLocation(response).orElse(exportUri);
        return createFuture(response, contentLocation);
    }

    private HttpPost buildHttpRequest(URI uri, String body) {
        HttpPost httpRequest = new HttpPost(uri);
        httpRequest.setHeader("Prefer", "respond-async");
        httpRequest.setHeader("Content-Type", Constants.CT_JSON);

        StringEntity entity = new StringEntity(body, StandardCharsets.UTF_8);
        httpRequest.setEntity(entity);

        processInterceptors(httpRequest);

        return httpRequest;
    }

    /**
     * Resume a Bulk Data Export, given a polling status URI.
     *
     * @param contentLocation - URI of the status for the ongoing export
     * @return a future
     */
    public BDExportFuture resumeExport(URI contentLocation) throws IOException {
        logger.info("Resuming export: " + contentLocation);

        HttpResponse response = poll(contentLocation);
        return createFuture(response, contentLocation);
    }

    /**
     * Add an interceptor to the HTTP bulk data export request
     *
     * @param interceptor the interceptor to add
     */
    public void addInterceptor(HttpRequestInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    /**
     * Cancel a Bulk Data Export, given a polling location.
     *
     * @param contentLocation - URI of the status for the ongoing export
     * @throws IOException
     */
    public HttpResponse cancel(URI contentLocation) throws IOException {
        HttpDelete request = new HttpDelete(contentLocation);

        logger.info("Cancelling export '" + contentLocation + "'");

        processInterceptors(request);

        HttpResponse response = httpClient.execute(request);
        if (BDExportUtils.extractStatusCode(response) != STATUS_HTTP_202_ACCEPTED){
            throw new RuntimeException("Failed to cancel export: " + response.getStatusLine().getReasonPhrase());
        }

        return response;
    }

    /**
     * Poll an ongoing bulk data export
     *
     * @param contentLocation - URI of the status for the ongoing export
     * @return the HTTP response
     * @throws IOException
     */
    public HttpResponse poll(URI contentLocation) throws IOException {
        HttpGet request = new HttpGet(contentLocation);

        logger.info("Polling status at '" + contentLocation + "'");

        processInterceptors(request);

        return httpClient.execute(request);
    }

    private void processInterceptors(HttpRequest request) {
        for (HttpRequestInterceptor interceptor : interceptors) {
            try {
                interceptor.process(request, new BasicHttpContext());
            } catch (HttpException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public BDExportFuture createFuture(HttpResponse response, URI locationUri) {
        int statusCode = BDExportUtils.extractStatusCode(response);

        if (statusCode == STATUS_HTTP_200_OK) {
            return new CompletedExportFuture(response, locationUri);
        } else if (statusCode == STATUS_HTTP_202_ACCEPTED) {
            return new OngoingExportFuture(fhirContext, httpClient, locationUri);
        } else if (statusCode >= 400 && statusCode <= 599) {
            return new ErrorExportFuture(fhirContext, response, locationUri);
        } else {
            throw new RuntimeException("Export failed, server responded with: " + statusCode);
        }
    }
}
