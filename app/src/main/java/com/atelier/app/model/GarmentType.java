package com.atelier.app.model;

public final class GarmentType {
    public final long id;
    public final String name;
    public final boolean archived;

    public GarmentType(long id, String name, boolean archived) {
        this.id = id;
        this.name = name;
        this.archived = archived;
    }
}
