package com.gathin.tvhome;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded, in-memory snapshots for one unsaved tile-order editing session. */
final class ReorderHistory {
    static final int LIMIT = 64;

    static final class Snapshot {
        final List<String> keys;
        final Set<String> dockKeys;

        Snapshot(List<String> keys, Set<String> dockKeys) {
            this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
            this.dockKeys = Collections.unmodifiableSet(new HashSet<>(dockKeys));
        }
    }

    private final Deque<Snapshot> history = new ArrayDeque<>();

    void record(List<String> keys, Set<String> dockKeys) {
        if (history.size() == LIMIT) history.removeFirst();
        history.addLast(new Snapshot(keys, dockKeys));
    }

    Snapshot undo() {
        return history.pollLast();
    }

    boolean canUndo() {
        return !history.isEmpty();
    }

    int size() {
        return history.size();
    }

    void clear() {
        history.clear();
    }

    /** Returns the target-position moves needed to restore the snapshot, in safe application order. */
    static List<int[]> restorationMoves(List<String> current, List<String> target) {
        List<String> working = new ArrayList<>(current);
        List<String> presentTarget = new ArrayList<>();
        for (String key : target) if (working.contains(key) && !presentTarget.contains(key)) presentTarget.add(key);
        for (String key : working) if (!presentTarget.contains(key)) presentTarget.add(key);
        List<int[]> moves = new ArrayList<>();
        for (int position = 0; position < presentTarget.size(); position++) {
            String key = presentTarget.get(position);
            int from = working.indexOf(key);
            if (from < 0 || from == position) continue;
            moves.add(new int[]{from, position});
            working.add(position, working.remove(from));
        }
        return moves;
    }
}
