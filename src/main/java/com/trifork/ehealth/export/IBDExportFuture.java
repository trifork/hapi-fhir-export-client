package com.trifork.ehealth.export;

import java.net.URI;
import java.util.concurrent.Future;

public interface IBDExportFuture extends Future<BDExportResponse> {
    URI getPollingUri();
}
