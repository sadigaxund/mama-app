package com.mama.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Order stage. Move rules are in docs/design.md, "Allowed moves"; StageTest holds them. */
public enum Stage {
    ACCEPTED, CUTTING, FITTING_1, FITTING_2, ALTERATIONS, DONE;

    /** The stage one step back, or null for ACCEPTED. */
    public Stage previous() {
        switch (this) {
            case CUTTING: return ACCEPTED;
            case FITTING_1: return CUTTING;
            case FITTING_2: return FITTING_1;
            case ALTERATIONS: return FITTING_2;
            case DONE: return FITTING_2;
            default: return null;
        }
    }

    /** The primary forward move for the arrow button, or null for DONE. */
    public Stage next() {
        switch (this) {
            case ACCEPTED: return CUTTING;
            case CUTTING: return FITTING_1;
            case FITTING_1: return FITTING_2;
            case FITTING_2: return DONE;
            case ALTERATIONS: return DONE;
            default: return null;
        }
    }

    private List<Stage> forwardMoves() {
        switch (this) {
            case ACCEPTED: return Collections.singletonList(CUTTING);
            case CUTTING: return Collections.singletonList(FITTING_1);
            case FITTING_1: return Collections.singletonList(FITTING_2);
            case FITTING_2: return Collections.singletonList(ALTERATIONS);
            default: return Collections.emptyList();
        }
    }

    /** Forward moves, DONE from any other stage, and the previous stage. No duplicates. */
    public List<Stage> allowedMoves() {
        List<Stage> moves = new ArrayList<>(forwardMoves());
        if (this != DONE && !moves.contains(DONE)) {
            moves.add(DONE);
        }
        Stage prev = previous();
        if (prev != null && !moves.contains(prev)) {
            moves.add(prev);
        }
        return Collections.unmodifiableList(moves);
    }

    /** True for the stages that offer to book a visit when an order moves into them. */
    public boolean offersVisit() {
        return this == FITTING_1 || this == FITTING_2 || this == DONE;
    }

    public boolean canMoveTo(Stage target) {
        return target != null && allowedMoves().contains(target);
    }

    public boolean isActive() {
        return this != DONE;
    }
}
