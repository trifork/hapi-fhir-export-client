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

    /**
     * Set the polling interval manually, and ignore the 'retry-after' header, returned by the bulk export.
     *
     * @param millis polling interval in milliseconds
     */
    BDExportFuture setPollingInterval(Integer millis);
}
