package com.mama.app.data;

/** Thrown when a garment type name already exists, ignoring case. */
public class DuplicateGarmentTypeException extends RuntimeException {
    public DuplicateGarmentTypeException(String name) {
        super("Garment type already exists: " + name);
    }
}
