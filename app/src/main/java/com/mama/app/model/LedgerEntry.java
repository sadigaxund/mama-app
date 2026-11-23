package com.mama.app.model;

public final class LedgerEntry {
    public final long id;
    public final long orderId;
    public final LedgerType type;
    public final long amount;
    public final String title;
    public final long createdAt;

    public LedgerEntry(long id, long orderId, LedgerType type, long amount, String title, long createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.type = type;
        this.amount = amount;
        this.title = title;
        this.createdAt = createdAt;
    }
}
