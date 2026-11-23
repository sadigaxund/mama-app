package com.mama.app.model;

import java.util.Comparator;

/** Board filter: an optional kind and an optional garment type. Immutable. */
public final class OrderFilter {
    /** No filter: matches every order. */
    public static final OrderFilter NONE = new OrderFilter(null, 0);

    /** Null means any kind. */
    public final Kind kind;
    /** Zero means any type. Real ids start at 1. */
    public final long garmentTypeId;

    public OrderFilter(Kind kind, long garmentTypeId) {
        this.kind = kind;
        this.garmentTypeId = garmentTypeId;
    }

    public boolean isActive() {
        return kind != null || garmentTypeId != 0;
    }

    public boolean matches(Order o) {
        return (kind == null || o.kind == kind)
                && (garmentTypeId == 0 || o.garmentTypeId == garmentTypeId);
    }

    public OrderFilter withKind(Kind newKind) {
        return new OrderFilter(newKind, garmentTypeId);
    }

    public OrderFilter withGarmentType(long id) {
        return new OrderFilter(kind, id);
    }

    /**
     * Search result order: active stages first by deadline then id, then DONE orders by
     * stage_changed_at descending then id descending.
     */
    public static final Comparator<Order> SEARCH_ORDER = (a, b) -> {
        boolean aDone = a.stage == Stage.DONE;
        boolean bDone = b.stage == Stage.DONE;
        if (aDone != bDone) {
            return aDone ? 1 : -1;
        }
        if (aDone) {
            int c = Long.compare(b.stageChangedAt, a.stageChangedAt);
            return c != 0 ? c : Long.compare(b.id, a.id);
        }
        int c = a.deadline.compareTo(b.deadline);
        return c != 0 ? c : Long.compare(a.id, b.id);
    };
}
