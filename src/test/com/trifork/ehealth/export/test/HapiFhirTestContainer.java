package com.trifork.ehealth.export.test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class HapiFhirTestContainer {
    private static final int HAPI_PORT = 8080;

    private final GenericContainer<?> hapiFhirTestContainer;

    public HapiFhirTestContainer() {
        this.hapiFhirTestContainer = createHapiFhirTestContainer();
    }

    public void start() {
        this.hapiFhirTestContainer.start();
    }

    public void stop() {
        this.hapiFhirTestContainer.stop();
    }

    public IGenericClient createHapiFhirClient() {
        if (!hapiFhirTestContainer.isRunning()) {
            throw new RuntimeException("Run the container first..");
        }

        String containerUrl = String.format(
                "http://%s:%s/fhir",
                this.hapiFhirTestContainer.getMappedPort(HAPI_PORT),
                this.hapiFhirTestContainer.getHost()
        );

        return FhirContext.forR4().newRestfulGenericClient(containerUrl);
    }

    private static GenericContainer<?> createHapiFhirTestContainer() {
        return new GenericContainer(DockerImageName.parse("registry.hub.docker.com/hapiproject/hapi:v6.8.0"))
                .withExposedPorts(HAPI_PORT)
                .withEnv("hapi.fhir.fhir_version", "R4")
                .withEnv("hapi.fhir.bulk_export_enabled ", "true")
                .waitingFor(Wait.forHttp("/fhir/metadata").withStartupTimeout(Duration.ofMinutes(2)));
    }
}
