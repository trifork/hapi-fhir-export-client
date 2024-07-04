package com.trifork.ehealth.export;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class BDExportUtils {
    public static Optional<String> extractProgress(HttpResponse response) {
        Header[] progressHeaders = response.getHeaders("x-progress");
        if (progressHeaders.length > 0) {
            return Optional.of(progressHeaders[0].getValue());
        }

        return Optional.empty();
    }

    public static boolean isCancelled(HttpResponse response) {
        Header[] headers = response.getHeaders("x-progress");

        return headers.length > 0 && headers[0].getValue().contains("CANCELLED");
    }

    public static Optional<Integer> extractRetryAfterInSeconds(HttpResponse response) {
        Header[] retryHeaders = response.getHeaders("retry-after");
        if (retryHeaders.length > 0) {
            String duration = retryHeaders[0].getValue();
            try {
                return Optional.of(Integer.parseInt(duration));
            } catch (NumberFormatException e) {
                // TODO: Handle dates instead.
            }
        }

        return Optional.empty();
    }

    public static Instant evaluateNextAllowedPollTime(Integer retryAfterInSeconds) {
        return Instant.now().plus(retryAfterInSeconds, ChronoUnit.SECONDS);
    }

    public static Optional<URI> extractContentLocation(HttpResponse response) {
        if (response.containsHeader("content-location")) {
            return Optional.of(response.getHeaders("content-location")[0])
                    .map(Header::getValue)
                    .map(URI::create);
        }

        return Optional.empty();
    }

    public static int extractStatusCode(HttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }
}
