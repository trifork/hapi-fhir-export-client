package com.trifork.ehealth.export;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class BDExportUtils {
    public static boolean isCancelled(HttpResponse response) {
        Header[] headers = response.getHeaders("x-progress");

        return headers.length > 0 && headers[0].getValue().contains("CANCELLED");
    }

    public static Instant evaluateNextAllowedPollTime(HttpResponse response) {
        Header[] retryHeaders = response.getHeaders("retry-after");
        if (retryHeaders.length > 0) {
            String duration = retryHeaders[0].getValue();
            try {
                int durationInSeconds = Integer.parseInt(duration);

                return Instant.now().plus(durationInSeconds, ChronoUnit.SECONDS);
            } catch (NumberFormatException e) {
                // TODO: Handle dates instead.
            }
        }

        return Instant.now();
    }

    public static URI extractContentLocation(HttpResponse response) {
        return URI.create(response.getHeaders("content-location")[0].getValue());
    }
}
