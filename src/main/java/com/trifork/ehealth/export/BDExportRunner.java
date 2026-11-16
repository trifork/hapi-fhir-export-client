package com.trifork.ehealth.export;

import ca.uhn.fhir.context.FhirContext;
import com.trifork.ehealth.export.exceptions.BDExportFailedException;
import com.trifork.ehealth.export.exceptions.BDExportTimeoutException;
import com.trifork.ehealth.export.future.BDExportFuture;
import com.trifork.ehealth.export.response.BDExportResponse;
import com.trifork.ehealth.export.response.BDExportResultResponse;
import com.trifork.ehealth.export.response.BDExportResultResponse.OutputItem;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Starts a bulk data export and waits for the server to finish the export.
 */
public final class BDExportRunner {

    private BDExportRunner() {
    }

    /**
     * Starts the export, polls the server until the export has finished, and returns the output items of the result.
     * The list is empty when the result holds no output.
     *
     * <p>When the export does not finish within {@code timeout}, the method throws and does <b>not</b> cancel the
     * export, so the job can still be running on the server. A retry that sends identical parameters can be handed
     * that same job, so a caller that retries should change the parameters of the request, for example by moving
     * {@code _since} back a random amount.
     *
     * @param client                     the client that starts and polls the export
     * @param request                    the export to start
     * @param timeout                    how long to wait for the server to finish the export
     * @param pollingInterval            how long to wait between two polls; replaces the retry-after of the server
     * @param fhirContext                used to write an OperationOutcome into the message of an error
     * @throws IOException               if the export cannot be started
     * @throws InterruptedException      if the waiting thread is interrupted, or the export is cancelled by someone else
     * @throws BDExportTimeoutException  if the export does not finish within {@code timeout}
     * @throws BDExportFailedException   if the server reports an error, or the result holds error items or nothing
     */
    public static List<OutputItem> exportAndWait(
            BDExportClient client,
            BDExportRequest request,
            Duration timeout,
            Duration pollingInterval,
            FhirContext fhirContext
    ) throws IOException, InterruptedException {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(pollingInterval, "pollingInterval");
        Objects.requireNonNull(fhirContext, "fhirContext");

        BDExportFuture future = client.initiate(request);
        future.setPollingInterval(Math.toIntExact(pollingInterval.toMillis()));

        BDExportResponse response;
        try {
            response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new BDExportTimeoutException(
                    "The export at " + future.getLocationURI() + " did not finish within " + timeout, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new BDExportFailedException("The export at " + future.getLocationURI() + " failed", cause);
        }

        Optional<OperationOutcome> error = response.getError();
        if (error.isPresent()) {
            throw new BDExportFailedException("The export at " + future.getLocationURI() + " failed: "
                    + fhirContext.newJsonParser().encodeResourceToString(error.get()));
        }

        BDExportResultResponse result = response.getResult().orElseThrow(() -> new BDExportFailedException(
                "The export at " + future.getLocationURI() + " returned neither a result nor an error"));

        List<OutputItem> errorItems = result.getError();
        if (errorItems != null && !errorItems.isEmpty()) {
            String urls = errorItems.stream().map(OutputItem::getUrl).collect(Collectors.joining(", "));
            throw new BDExportFailedException(
                    "The export at " + future.getLocationURI() + " finished with errors: " + urls);
        }

        List<OutputItem> output = result.getOutput();
        return output == null ? List.of() : output;
    }
}
