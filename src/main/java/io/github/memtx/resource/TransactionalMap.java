package io.github.memtx.resource;

import io.github.memtx.tx.TransactionManager;
import io.github.memtx.tx.TransactionParticipant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Transactional map that keeps a delta log per transaction and only mutates shared state on commit.
 */
public final class TransactionalMap<K, V> implements TransactionParticipant<TransactionalMap.Stage<K, V>> {

    private final Object monitor = new Object();
    private final TransactionManager transactions;
    private final LinkedHashMap<K, V> committed = new LinkedHashMap<>();

    public TransactionalMap(TransactionManager transactions) {
        this.transactions = transactions;
    }

    public V get(K key) {
        Stage<K, V> state = transactions.stateIfPresent(this);
        if (state != null) {
            if (state.writes.containsKey(key)) {
                return state.writes.get(key);
            }
            if (state.removals.contains(key) || state.cleared) {
                return null;
            }
        }
        synchronized (monitor) {
            return committed.get(key);
        }
    }

    public void put(K key, V value) {
        Stage<K, V> state = stageOrNull();
        if (state == null) {
            synchronized (monitor) {
                committed.put(key, value);
            }
            return;
        }

        state.removals.remove(key);
        state.writes.put(key, value);
    }

    public V remove(K key) {
        Stage<K, V> state = stageOrNull();
        if (state == null) {
            synchronized (monitor) {
                return committed.remove(key);
            }
        }

        V previous = get(key);
        state.writes.remove(key);
        state.removals.add(key);
        return previous;
    }

    public void clear() {
        Stage<K, V> state = stageOrNull();
        if (state == null) {
            synchronized (monitor) {
                committed.clear();
            }
            return;
        }

        state.cleared = true;
        state.writes.clear();
        state.removals.clear();
    }

    public boolean containsKey(K key) {
        Stage<K, V> state = transactions.stateIfPresent(this);
        if (state != null) {
            if (state.writes.containsKey(key)) {
                return true;
            }
            if (state.removals.contains(key) || state.cleared) {
                return false;
            }
        }
        synchronized (monitor) {
            return committed.containsKey(key);
        }
    }

    public int size() {
        return snapshotView().size();
    }

    public Map<K, V> snapshotCommitted() {
        synchronized (monitor) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(committed));
        }
    }

    public Map<K, V> snapshotView() {
        Stage<K, V> state = transactions.stateIfPresent(this);
        LinkedHashMap<K, V> view;
        synchronized (monitor) {
            view = new LinkedHashMap<>(committed);
        }
        if (state == null) {
            return Collections.unmodifiableMap(view);
        }
        if (state.cleared) {
            view.clear();
        }
        for (K removed : state.removals) {
            view.remove(removed);
        }
        view.putAll(state.writes);
        return Collections.unmodifiableMap(view);
    }

    @Override
    public Stage<K, V> createState() {
        return new Stage<>();
    }

    @Override
    public void commit(Stage<K, V> state) {
        synchronized (monitor) {
            if (state.cleared) {
                committed.clear();
            }
            for (K removed : state.removals) {
                committed.remove(removed);
            }
            committed.putAll(state.writes);
        }
    }

    private Stage<K, V> stageOrNull() {
        if (!transactions.hasCurrentTransaction()) {
            return null;
        }
        return transactions.stateFor(this);
    }

    static final class Stage<K, V> {
        private final Map<K, V> writes = new HashMap<>();
        private final Set<K> removals = new HashSet<>();
        private boolean cleared;
    }
}
