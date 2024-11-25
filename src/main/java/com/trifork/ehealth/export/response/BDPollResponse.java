package com.trifork.ehealth.export.response;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static ca.uhn.fhir.rest.api.Constants.STATUS_HTTP_200_OK;
import static ca.uhn.fhir.rest.api.Constants.STATUS_HTTP_202_ACCEPTED;

public class BDPollResponse {
    private final FhirContext fhirContext;
    private final int statusCode;
    private final Map<String, String> headers;
    private final InputStream requestBodyStream;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(BDPollResponse.class);

    private static final int STATUS_HTTP_429_TOO_MANY_REQUESTS = 429;

    public BDPollResponse(
            FhirContext fhirContext,
            int statusCode,
            @NotNull Map<String, String> headers,
            @Nullable InputStream requestBodyStream
    ) {
        this.fhirContext = fhirContext;
        this.statusCode = statusCode;
        this.headers = headers.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toLowerCase(),
                        Map.Entry::getValue,
                        (existing, replacement) -> replacement,
                        HashMap::new
                ));
        this.requestBodyStream = requestBodyStream;
    }

    public int getStatusCode() {
        return statusCode;
    }

    private Optional<String> getHeader(String name) {
        if (headers.containsKey(name)) {
            return Optional.of(headers.get(name));
        }

        return Optional.empty();
    }

    public Optional<String> getProgressHeader() {
        return getHeader("x-progress");
    }

    public boolean isOngoing() {
        return statusCode == STATUS_HTTP_202_ACCEPTED;
    }

    public boolean isCompleted() {
        return statusCode == STATUS_HTTP_200_OK;
    }

    public boolean isError() {
        return statusCode != STATUS_HTTP_429_TOO_MANY_REQUESTS && statusCode >= 400 && statusCode <= 599;
    }

    public boolean isDone() {
        return isCompleted() || isError();
    }

    public boolean isCancelled() {
        Optional<String> opt = getProgressHeader();
        return opt.isPresent() && opt.get().contains("CANCELLED");
    }

    public Optional<Integer> getRetryAfterInSecondsOpt() {
        Optional<String> opt = getHeader("retry-after");
        if (opt.isPresent()) {
            try {
                return Optional.of(Integer.parseInt(opt.get()));
            } catch (NumberFormatException e) {
                // TODO: Handle dates instead.
            }
        }

        return Optional.empty();
    }

    public Optional<Instant> getNextAllowedPollTime() {
        return getRetryAfterInSecondsOpt().map(seconds -> Instant.now().plus(seconds, ChronoUnit.SECONDS));
    }

    public Optional<BDExportResultResponse> getResultOpt() {
        if (isCompleted() && requestBodyStream != null) {
            try {
                byte[] bytes = requestBodyStream.readAllBytes();
                logger.info("Reading " + bytes.length + " bytes from 'Bulk Data Export'");

                return Optional.of(objectMapper.readValue(bytes, BDExportResultResponse.class));
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse export results", e);
            }
        }
        return Optional.empty();
    }

    public Optional<OperationOutcome> getErrorOpt() {
        if (isError() && requestBodyStream != null) {
            try {
                OperationOutcome operationOutcome = fhirContext.newJsonParser().parseResource(OperationOutcome.class, requestBodyStream);
                return Optional.of(operationOutcome);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse error details", e);
            }
        }
        return Optional.empty();
    }
}
