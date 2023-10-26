package com.trifork.ehealth.export;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class BDExportUtils {
    public static boolean isCancelled(HttpHeaders headers) {
        Optional<String> opt = headers.firstValue("x-progress");
        return opt.isPresent() && opt.get().contains("CANCELLED");
    }

    public static Optional<String> extractProgress(HttpHeaders headers) {
        return headers.firstValue("x-progress");
    }

    public static Instant evaluateNextAllowedPollTime(HttpHeaders headers) {
        Optional<String> opt = headers.firstValue("retry-after");
        if (opt.isPresent()) {
            String duration = opt.get();
            try {
                int durationInSeconds = Integer.parseInt(duration);

                return Instant.now().plus(durationInSeconds, ChronoUnit.SECONDS);
            } catch (NumberFormatException e) {
                // TODO: Handle dates instead.
            }
        }

        return Instant.now();
    }

    public static URI extractContentLocation(HttpResponse<String> response) {
        return URI.create(response.headers().firstValue("content-location").orElseThrow());
    }
}
