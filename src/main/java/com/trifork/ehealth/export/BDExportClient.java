package com.trifork.ehealth.export;

public class BDExportClient {
    private HapiFhirExportClient exportClient;

    public BDExportClient(HapiFhirExportClient exportClient) {
        this.exportClient = exportClient;
    }

    public BDExportFuture bulkDataExport(BDExportRequest request) {
        return new BDExportFuture(exportClient, request);
    }
}
