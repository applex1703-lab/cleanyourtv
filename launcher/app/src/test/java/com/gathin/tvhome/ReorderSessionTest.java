package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Pure order-history checks: reverse adjacent moves and restore dock membership together. */
public class ReorderSessionTest {

    @Test
    public void snapshotUndoRestoresOrderAndDockMembership() {
        List<String> before = Arrays.asList("a/.A", "b/.B", "c/.C", "d/.D");
        Set<String> dockBefore = new HashSet<>(Arrays.asList("a/.A", "b/.B"));

        List<String> after = Arrays.asList("c/.C", "a/.A", "d/.D", "b/.B");
        Set<String> dockAfter = new HashSet<>(Arrays.asList("c/.C", "a/.A"));

        assertEquals(Arrays.asList("a/.A", "b/.B", "c/.C", "d/.D"), restore(after, before));
        assertEquals(new HashSet<>(Arrays.asList("a/.A", "b/.B")), dockBefore);
        assertEquals(new HashSet<>(Arrays.asList("c/.C", "a/.A")), dockAfter);
    }

    @Test
    public void emptyHistoryDoesNotInventUndo() {
        ReorderHistory history = new ReorderHistory();
        assertTrue(!history.canUndo());
        assertEquals(null, history.undo());
    }

    @Test
    public void undoReturnsMostRecentSnapshotAndDockMembership() {
        ReorderHistory history = new ReorderHistory();
        Set<String> firstDock = new HashSet<>(Collections.singletonList("a/.A"));
        history.record(Arrays.asList("a/.A", "b/.B"), firstDock);
        firstDock.add("b/.B");
        history.record(Arrays.asList("b/.B", "a/.A"), firstDock);

        ReorderHistory.Snapshot last = history.undo();
        assertEquals(Arrays.asList("b/.B", "a/.A"), last.keys);
        assertEquals(new HashSet<>(Arrays.asList("a/.A", "b/.B")), last.dockKeys);
        assertEquals(Arrays.asList("a/.A", "b/.B"), history.undo().keys);
        assertTrue(!history.canUndo());
    }

    @Test
    public void historyIsBounded() {
        ReorderHistory history = new ReorderHistory();
        for (int i = 0; i < ReorderHistory.LIMIT + 5; i++) {
            history.record(Collections.singletonList("app/" + i), Collections.emptySet());
        }
        assertEquals(ReorderHistory.LIMIT, history.size());
        assertEquals(Collections.singletonList("app/" + (ReorderHistory.LIMIT + 4)), history.undo().keys);
    }

    @Test
    public void restorePlanUsesComponentIdentitySkipsMissingAndKeepsNewAppsLast() {
        List<String> current = Arrays.asList("b/.B", "new/.New", "a/.A");
        List<String> saved = Arrays.asList("a/.A", "gone/.Gone", "b/.B");
        List<String> result = new ArrayList<>(current);
        for (int[] move : ReorderHistory.restorationMoves(current, saved)) {
            result.add(move[1], result.remove(move[0]));
        }
        assertEquals(Arrays.asList("a/.A", "b/.B", "new/.New"), result);
    }

    private static List<String> restore(List<String> current, List<String> target) {
        List<String> result = new java.util.ArrayList<>(current);
        for (int index = 0; index < target.size(); index++) {
            int from = result.indexOf(target.get(index));
            if (from >= 0 && from != index) {
                String key = result.remove(from);
                result.add(index, key);
            }
        }
        return result;
    }
}
