package com.trifork.ehealth.export;

import com.trifork.ehealth.export.future.BDExportFuture;
import com.trifork.ehealth.export.response.BDCancelResponse;
import com.trifork.ehealth.export.response.BDPollResponse;

import java.io.IOException;
import java.net.URI;

public interface BDExportClient {
    BDExportFuture initiate(BDExportRequest request) throws IOException;

    BDExportFuture resumeExport(URI contentLocation);

    BDCancelResponse cancel(URI contentLocation) throws IOException;

    BDPollResponse poll(URI contentLocation) throws IOException;
}
