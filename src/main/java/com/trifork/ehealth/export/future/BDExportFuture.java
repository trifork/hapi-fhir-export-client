package com.trifork.ehealth.export.future;

import com.trifork.ehealth.export.response.BDExportResponse;

import java.net.URI;
import java.util.concurrent.Future;

public interface BDExportFuture extends Future<BDExportResponse> {
    /**
     * Get the polling URI
     *
     * @return
     */
    URI getLocationURI();
}
