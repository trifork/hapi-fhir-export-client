package com.trifork.ehealth.export.test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

public class HapiFhirTestContainer {
    private static final int HAPI_PORT = 8080;

    private final GenericContainer<?> hapiFhirTestContainer;

    public HapiFhirTestContainer() {
        this.hapiFhirTestContainer = createHapiFhirTestContainer();
    }

    public void start() throws InterruptedException {
        this.hapiFhirTestContainer.start();

        // Give HAPI FHIR a chance to start up properly, so job definitions don't fail.
        Thread.sleep(60000);
    }

    public void stop() {
        this.hapiFhirTestContainer.stop();
    }

    public URI getHapiFhirUri() {
        if (!hapiFhirTestContainer.isRunning()) {
            throw new RuntimeException("Run the container first..");
        }

        String containerUrl = String.format(
                "http://%s:%s/fhir/",
                this.hapiFhirTestContainer.getHost(),
                this.hapiFhirTestContainer.getMappedPort(HAPI_PORT)
        );

        return URI.create(containerUrl);
    }

    private static GenericContainer<?> createHapiFhirTestContainer() {
        return new GenericContainer(DockerImageName.parse("registry.hub.docker.com/hapiproject/hapi:v6.8.0"))
                .withExposedPorts(HAPI_PORT)
                .withEnv("hapi.fhir.fhir_version", "R4")
                .withEnv("hapi.fhir.bulk_export_enabled ", "true")
                .withEnv("hapi.fhir.binary_storage_enabled", "true")
                .waitingFor(Wait.forHttp("/fhir/metadata").withStartupTimeout(Duration.ofMinutes(2)));
    }
}
