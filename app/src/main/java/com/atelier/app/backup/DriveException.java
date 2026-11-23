package com.atelier.app.backup;

/** A Drive or token failure, sorted into the four reasons Settings can explain. */
public final class DriveException extends Exception {
    public enum Reason { AUTH, NETWORK, QUOTA, OTHER }

    public final Reason reason;
    public final int httpStatus;

    public DriveException(Reason reason, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.httpStatus = httpStatus;
    }

    public DriveException(Reason reason, int httpStatus, String message) {
        this(reason, httpStatus, message, null);
    }
}
