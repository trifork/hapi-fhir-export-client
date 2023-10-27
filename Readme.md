# HAPI FHIR Bulk Data Export

This project aims at simplifying the Bulk Data Export process in HAPI FHIR, 
and which is documented [here](https://hl7.org/fhir/uv/bulkdata/export/index.html)

## Configuring the client

To create a client, we need a FhirContext which can contain custom configured parsers.
We also need a HttpClient, which comes configured with all the necessary security/interceptors and other
properties specific to the application.

```java
        FhirContext fhirContext = FhirContext.forR4();
        httpClient = HttpClient.newHttpClient();
        BDExportClient exportClient = new BDExportClient(fhirContext, httpClient);
```

## Setting up the request
To be able to initiate a bulk data export, we have put the options together in 
[BDExportRequest](src/main/java/com/trifork/ehealth/export/BDExportRequest.java)

Example of a request, following the examples given in the 
[Experimental Query Parameters](https://hl7.org/fhir/uv/bulkdata/export/index.html#experimental-query-parameters):

```java
    ...
        
    BDExportRequest request = 
        new BDExportRequest(URI.create("http://myserver.example.org/fhir/$export"))
            .setOutputFormat(Constants.CT_FHIR_NDJSON)
            .addType(ResourceType.MedicationRequest)
            .addType(ResourceType.Condition)
            .setSince(new InstantType(new GregorianCalendar(2023, Calendar.OCTOBER, 27)))
            .addTypeFilter(
                new BDExportTypeFilter(
                    ResourceType.MedicationRequest,
                    "status",
                    new TokenParam("active")
                )
            )
            .addTypeFilter(
                new BDExportTypeFilter(
                    ResourceType.MedicationRequest,
                    "status",
                    new TokenParam("completed")
                ).and(
                    "date",
                    new DateParam(
                        ParamPrefixEnum.GREATERTHAN,
                        Date.from(Instant.now().minus(2, ChronoUnit.DAYS))
                    )
                )
        );
                
    ...
```

When a [BDExportRequest](src/main/java/com/trifork/ehealth/export/BDExportRequest.java) has been constructed,
we can go ahead and initiate an export.

## Initiating an export

Assuming the client has been configured, and a request has been made, a request can then be initiated, which returns a
[BDExportFuture](src/main/java/com/trifork/ehealth/export/BDExportFuture.java), where the result will eventually be available.

```java
    ...

        BDExportFuture future = exportClient.startExport(request);
        
    ...
```

## Resuming an export
If the application crashes, while it awaits the future, or another thread should pick up the responsibility for
fetching the export result, it is possible to resume an ongoing export, given the polling location of the export

Example:

```java
    ...

        BDExportFuture future = exportClient.startExport(request);
        URI pollingUri = future.getPollingUri();
        
        ...

        BDExportFuture futureForTheSameExport = exportClient.resumeExport(pollingUri)
    ...
```