package com.mama.app.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class StageTest {
    private static Set<Stage> set(Stage... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    private static Set<Stage> expected(Stage from) {
        switch (from) {
            case ACCEPTED: return set(Stage.CUTTING, Stage.DONE);
            case CUTTING: return set(Stage.FITTING_1, Stage.DONE, Stage.ACCEPTED);
            case FITTING_1: return set(Stage.FITTING_2, Stage.DONE, Stage.CUTTING);
            case FITTING_2: return set(Stage.ALTERATIONS, Stage.DONE, Stage.FITTING_1);
            case ALTERATIONS: return set(Stage.DONE, Stage.FITTING_2);
            default: return set(Stage.FITTING_2);
        }
    }

    @Test
    public void everyPairMatchesTheTable() {
        for (Stage from : Stage.values()) {
            for (Stage to : Stage.values()) {
                assertEquals(from + " -> " + to, expected(from).contains(to), from.canMoveTo(to));
            }
        }
    }

    @Test
    public void allowedMovesMatchTheTableWithoutDuplicates() {
        for (Stage from : Stage.values()) {
            assertEquals(from.toString(), expected(from), new HashSet<>(from.allowedMoves()));
            assertEquals(from.toString(), expected(from).size(), from.allowedMoves().size());
        }
    }

    @Test
    public void noStageMovesToItself() {
        for (Stage s : Stage.values()) {
            assertFalse(s.canMoveTo(s));
        }
    }

    @Test
    public void nullTargetIsRejected() {
        assertFalse(Stage.ACCEPTED.canMoveTo(null));
    }

    @Test
    public void previous() {
        assertNull(Stage.ACCEPTED.previous());
        assertEquals(Stage.ACCEPTED, Stage.CUTTING.previous());
        assertEquals(Stage.CUTTING, Stage.FITTING_1.previous());
        assertEquals(Stage.FITTING_1, Stage.FITTING_2.previous());
        assertEquals(Stage.FITTING_2, Stage.ALTERATIONS.previous());
        assertEquals(Stage.FITTING_2, Stage.DONE.previous());
    }

    @Test
    public void next() {
        assertEquals(Stage.CUTTING, Stage.ACCEPTED.next());
        assertEquals(Stage.FITTING_1, Stage.CUTTING.next());
        assertEquals(Stage.FITTING_2, Stage.FITTING_1.next());
        assertEquals(Stage.DONE, Stage.FITTING_2.next());
        assertEquals(Stage.DONE, Stage.ALTERATIONS.next());
        assertNull(Stage.DONE.next());
    }

    @Test
    public void nextIsAlwaysAllowed() {
        for (Stage s : Stage.values()) {
            if (s.next() != null) {
                assertTrue(s.toString(), s.canMoveTo(s.next()));
            }
        }
    }

    @Test
    public void alterationsOnlyReachableFromFitting2() {
        for (Stage s : Stage.values()) {
            assertEquals(s.toString(), s == Stage.FITTING_2, s.canMoveTo(Stage.ALTERATIONS));
        }
    }
}
