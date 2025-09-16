package com.trifork.ehealth.export.future;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.trifork.ehealth.export.response.BDExportResponse;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public class BDFailedExportFuture implements BDExportFuture {
    private final FhirContext fhirContext;
    private final int statusCode;
    private final String body;

    public BDFailedExportFuture(FhirContext fhirContext, int statusCode, String body) {
        this.fhirContext = fhirContext;
        this.statusCode = statusCode;
        this.body = body;

        assert statusCode >= 400 && statusCode <= 599;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public BDExportResponse get() {
        return createErrorResponse();
    }

    @Override
    public BDExportResponse get(long timeout, TimeUnit unit) {
        return createErrorResponse();
    }

    private BDExportResponse createErrorResponse() {
        OperationOutcome oo;

        final String raw = body == null ? "" : body.trim();

        if (raw.isEmpty()) {
            oo = outcomeWithDiagnostics(statusCode, "Empty response body");
        } else {
            try {
                IParser parser = null;
                if (looksLikeJson(raw)) {
                    parser = fhirContext.newJsonParser();
                } else if (looksLikeXml(raw)) {
                    parser = fhirContext.newXmlParser();
                }

                if (parser != null) {
                    oo = parser.parseResource(OperationOutcome.class, raw);
                } else {
                    // Not JSON/XML -> treat as plain text
                    oo = outcomeWithDiagnostics(statusCode, "Unparseable non-FHIR response: " + abbreviate(raw, 400));
                }
            } catch (Exception e) {
                // Parsing failed -> fall back to plain-text diagnostics
                oo = outcomeWithDiagnostics(statusCode,
                        "Failed to parse error body as FHIR OperationOutcome: " + e.getClass().getSimpleName()
                                + " — body: " + abbreviate(raw, 400));
            }
        }

        return new BDExportResponse(getLocationURI(), statusCode, null, oo);
    }

    private static boolean looksLikeJson(String s) {
        char c = s.charAt(0);
        return c == '{' || c == '[';
    }

    private static boolean looksLikeXml(String s) {
        return s.charAt(0) == '<';
    }

    private static String abbreviate(String s, int max) {
        return (s.length() <= max) ? s : s.substring(0, max) + "…";
    }

    private static OperationOutcome outcomeWithDiagnostics(int status, String diagnostics) {
        OperationOutcome oo = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent issue = new OperationOutcome.OperationOutcomeIssueComponent();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.EXCEPTION);
        issue.setDiagnostics("HTTP " + status + ": " + diagnostics);
        oo.addIssue(issue);
        return oo;
    }

    @Override
    public URI getLocationURI() {
        return null;
    }

    @Override
    public BDFailedExportFuture setPollingInterval(Integer millis) {
        return this;
    }
}
