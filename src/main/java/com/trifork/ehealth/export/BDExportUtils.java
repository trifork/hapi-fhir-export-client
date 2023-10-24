package com.trifork.ehealth.export;

import java.net.http.HttpHeaders;
import java.util.Optional;

public class BDExportUtils {
    public static boolean isCancelled(HttpHeaders headers) {
        Optional<String> opt = headers.firstValue("x-progress");
        return opt.isPresent() && opt.get().contains("CANCELLED");
    }
}
