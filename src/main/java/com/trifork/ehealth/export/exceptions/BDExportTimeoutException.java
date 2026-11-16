package com.trifork.ehealth.export.exceptions;

import com.trifork.ehealth.export.BDExportRunner;

/**
 * The server did not finish an export within the time given to {@link BDExportRunner#exportAndWait}. The export is
 * not cancelled, so the job may still be running on the server.
 */
public class BDExportTimeoutException extends RuntimeException {
    public BDExportTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
