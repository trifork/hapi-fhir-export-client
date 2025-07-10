package com.trifork.ehealth.export.response;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

public class TestContainerFactory {
    private static GenericContainer<?> hapiTestContainer;

    public static GenericContainer<?> createHapiTestContainer() {
        if (hapiTestContainer == null) {
            hapiTestContainer = new GenericContainer<>("registry.hub.docker.com/hapiproject/hapi:v8.0.0")
                    .withExposedPorts(8080)
                    .withEnv("hapi.fhir.fhir_version", "R4")
                    .withEnv("hapi.fhir.bulk_export_enabled ", "true")
                    .withEnv("hapi.fhir.allow_multiple_delete ", "true")
                    .withEnv("hapi.fhir.delete_expunge_enabled ", "true")
                    .withEnv("hapi.fhir.expunge_enabled ", "true")
                    .withEnv("hapi.fhir.allow_cascading_deletes ", "true")
                    .withEnv("hapi.fhir.enforce_referential_integrity_on_delete ", "false")
                    .withEnv("hapi.fhir.custom-provider-classes", "ca.uhn.fhir.batch2.jobs.expunge.DeleteExpungeProvider");
            hapiTestContainer.waitingFor(Wait.forHttp("/fhir/metadata").withStartupTimeout(Duration.ofMinutes(5)));
        }

        hapiTestContainer.start();

        return hapiTestContainer;
    }
}
