package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import com.trifork.ehealth.export.future.BDExportFuture;
import com.trifork.ehealth.export.future.BDFailedExportFuture;
import com.trifork.ehealth.export.future.BDInitiatedExportFuture;
import com.trifork.ehealth.export.response.BDCancelResponse;
import com.trifork.ehealth.export.response.BDPollResponse;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.BasicHttpContext;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static ca.uhn.fhir.rest.api.Constants.STATUS_HTTP_202_ACCEPTED;

public class ApacheHttpClient4BDExportClient implements BDExportClient {
    private final FhirContext fhirContext;
    private final HttpClient httpClient;

    private final List<HttpRequestInterceptor> interceptors = new ArrayList<>();

    private static final Logger logger = LoggerFactory.getLogger(ApacheHttpClient4BDExportClient.class);

    public ApacheHttpClient4BDExportClient(FhirContext fhirContext, HttpClient httpClient) {
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

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode > 299) {
            try (InputStream stream = response.getEntity().getContent()) {
                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return new BDFailedExportFuture(fhirContext, statusCode, content);
            }
        }

        Header[] headers = response.getHeaders("content-location");
        if (headers.length == 0) {
            throw new RuntimeException("No Content-Location header found in response");
        }

        URI contentLocation = URI.create(headers[0].getValue());
        return new BDInitiatedExportFuture(this, contentLocation);
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
    public BDInitiatedExportFuture resumeExport(URI contentLocation) {
        logger.info("Resuming export: " + contentLocation);
        return new BDInitiatedExportFuture(this, contentLocation);
    }

    /**
     * Add an interceptor to the HTTP bulk data export request
     *
     * @param interceptor the interceptor to add
     */
    public ApacheHttpClient4BDExportClient addInterceptor(HttpRequestInterceptor interceptor) {
        interceptors.add(interceptor);
        return this;
    }

    /**
     * Cancel a Bulk Data Export, given a polling location.
     *
     * @param contentLocation - URI of the status for the ongoing export
     * @throws IOException
     */
    @Override
    public BDCancelResponse cancel(URI contentLocation) throws IOException {
        HttpDelete request = new HttpDelete(contentLocation);

        logger.info("Cancelling export '" + contentLocation + "'");

        processInterceptors(request);

        HttpResponse response = httpClient.execute(request);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != STATUS_HTTP_202_ACCEPTED) {
            throw new RuntimeException("Failed to cancel export: " + response.getStatusLine().getReasonPhrase());
        }

        return new BDCancelResponse(statusCode);
    }

    /**
     * Poll an ongoing bulk data export
     *
     * @param contentLocation - URI of the status for the ongoing export
     * @return the HTTP response
     * @throws IOException
     */
    public BDPollResponse poll(URI contentLocation) throws IOException {
        HttpGet request = new HttpGet(contentLocation);

        logger.info("Polling status at '" + contentLocation + "'");

        processInterceptors(request);

        HttpResponse response = httpClient.execute(request);
        HttpEntity entity = response.getEntity() != null ? new BufferedHttpEntity(response.getEntity()) : null;

        return new BDPollResponse(
                fhirContext,
                response.getStatusLine().getStatusCode(),
                Arrays.stream(response.getAllHeaders())
                        .collect(
                                Collectors.groupingBy(Header::getName,
                                        Collectors.mapping(Header::getValue, Collectors.joining(","))
                                )
                        ),
                entity != null ? entity.getContent() : null
        );
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
}
