package com.trifork.ehealth.export.response;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static ca.uhn.fhir.util.ClasspathUtil.loadResourceAsStream;
import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class TestBDPollResponse {
    private final FhirContext fhirContext = FhirContext.forR4();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void progress_header_is_present() {
        BDPollResponse response = new BDPollResponse(fhirContext,202, Map.of("x-progress", "50% completed"), null);

        Optional<String> progress = response.getProgressHeader();

        assertTrue(progress.isPresent());
        assertEquals("50% completed", progress.get());
    }

    @Test
    void progress_header_is_absent() {
        BDPollResponse response = new BDPollResponse(fhirContext,202, Map.of(), null);

        Optional<String> progress = response.getProgressHeader();

        assertFalse(progress.isPresent());
    }

    @Test
    void is_cancelled_when_progress_contains_cancelled() {
        BDPollResponse response = new BDPollResponse(fhirContext,202, Map.of("x-progress", "CANCELLED"), null);

        assertTrue(response.isCancelled());
    }

    @Test
    void is_not_cancelled_when_progress_does_not_contain_cancelled() {
        BDPollResponse response = new BDPollResponse(fhirContext,202, Map.of("x-progress", "50% completed"), null);

        assertFalse(response.isCancelled());
    }

    @Test
    void retry_after_in_seconds_is_parsed_correctly() {
        BDPollResponse response = new BDPollResponse(fhirContext,202, Map.of("retry-after", "30"), null);

        Optional<Integer> retryAfter = response.getRetryAfterInSecondsOpt();

        assertTrue(retryAfter.isPresent());
        assertEquals(30, retryAfter.get());
    }

    @Test
    void retry_after_in_seconds_is_empty_when_not_a_number() {
        BDPollResponse response = new BDPollResponse(fhirContext,202, Map.of("retry-after", "invalid-number"), null);

        Optional<Integer> retryAfter = response.getRetryAfterInSecondsOpt();

        assertFalse(retryAfter.isPresent());
    }

    @Test
    void next_allowed_poll_time_is_calculated_correctly_with_retry_after() {
        BDPollResponse response = new BDPollResponse(fhirContext,202, Map.of("retry-after", "30"), null);

        Instant now = Instant.now();
        Instant nextAllowedPollTime = response.getNextAllowedPollTime().get();

        assertTrue(nextAllowedPollTime.isAfter(now));
        assertTrue(nextAllowedPollTime.isBefore(now.plusSeconds(35))); // Allow slight timing variations
    }

    @Test
    void parses_completed_response_into_result() throws Exception {
        InputStream inputStream = loadResourceAsStream("completed_response.json");
        BDPollResponse response = new BDPollResponse(fhirContext,200, Map.of(), inputStream);

        Optional<BDExportResultResponse> resultOpt = response.getResultOpt();

        assertTrue(resultOpt.isPresent());
        BDExportResultResponse result = resultOpt.get();
        assertEquals(3, result.getOutput().size());

        assertEquals("Patient", result.getOutput().get(0).getType());
        assertEquals("https://example.com/output/patient_file_1.ndjson", result.getOutput().get(0).getUrl());

        assertEquals("Observation", result.getOutput().get(1).getType());
        assertEquals("https://example.com/output/observation_file_1.ndjson", result.getOutput().get(1).getUrl());

        assertEquals("Binary", result.getOutput().get(2).getType());
        assertEquals("https://example.com/output/binary_file_1.ndjson", result.getOutput().get(2).getUrl());
    }

    @Test
    void parses_error_response_into_operation_outcome() throws Exception {
        InputStream inputStream = loadResourceAsStream("error_response.json");
        BDPollResponse response = new BDPollResponse(fhirContext,500, Map.of(), inputStream);

        Optional<OperationOutcome> errorOpt = response.getErrorOpt();

        assertTrue(errorOpt.isPresent());
        OperationOutcome error = errorOpt.get();
        assertEquals("OperationOutcome", error.getResourceType().name());
        assertEquals(1, error.getIssue().size());
        assertEquals("error", error.getIssueFirstRep().getSeverity().toCode());
        assertEquals("processing", error.getIssueFirstRep().getCode().toCode());
        assertEquals("An internal timeout has occurred", error.getIssueFirstRep().getDetails().getText());
    }
}
