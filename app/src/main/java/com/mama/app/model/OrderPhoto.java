package com.mama.app.model;

public final class OrderPhoto {
    public final long id;
    public final long orderId;
    public final String fileName;
    public final long createdAt;

    public OrderPhoto(long id, long orderId, String fileName, long createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.fileName = fileName;
        this.createdAt = createdAt;
    }
}
