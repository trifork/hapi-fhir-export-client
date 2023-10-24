package com.trifork.ehealth.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class TestHapiFhirExportClient {
    private HttpClient httpClient;

    @BeforeEach
    void setup() {
        this.httpClient = mock(HttpClient.class);
    }

    @Test
    void handle_bulk_data_export_initiation_success() throws IOException, InterruptedException {


        /*ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(captor.capture(), any())).thenReturn()*/


    }
}
