package com.trifork.ehealth.export.exceptions;

/**
 * The server finished an export with an error: an error in the response, error items in the result, no result at
 * all, or a failure while the export was being awaited.
 */
public class BDExportFailedException extends RuntimeException {
    public BDExportFailedException(String message) {
        super(message);
    }

    public BDExportFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
