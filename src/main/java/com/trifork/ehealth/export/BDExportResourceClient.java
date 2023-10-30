package com.trifork.ehealth.export;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Binary;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Take a BDExportCompleteResult's items, and convert it to FHIR resources, by fetching the content from the result urls.
 */
public class BDExportResourceClient {
    private final IGenericClient hapiFhirClient;

    public BDExportResourceClient(IGenericClient hapiFhirClient) {
        this.hapiFhirClient = hapiFhirClient;
    }

    public BDExportResourceResult convert(BDExportResultResponse result) {
        return new BDExportResourceResult(
                result.getTransactionTime(),
                result.getRequest(),
                result.isRequiresAccessToken(),
                convertToResourceItems(result.getOutput()),
                convertToResourceItems(result.getError()),
                result.getExtension()
        );
    }

    protected List<BDExportResourceResult.ResourceItem> convertToResourceItems(
            List<BDExportResultResponse.OutputItem> outputItems
    ) {
        if (outputItems == null || outputItems.isEmpty()) {
            return Collections.emptyList();
        }

        return outputItems.stream().map(this::convertToResourceItem).collect(Collectors.toList());
    }

    protected BDExportResourceResult.ResourceItem convertToResourceItem(BDExportResultResponse.OutputItem output) {
        Binary binary = fetchBinary(output.getUrl());
        return new BDExportResourceResult.ResourceItem(output.getType(), binary);
    }

    protected Binary fetchBinary(String url) {
        return hapiFhirClient.read()
                .resource(Binary.class)
                .withUrl(url)
                .execute();
    }
}
