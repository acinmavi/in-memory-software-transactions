package io.github.memtx.resource;

import io.github.memtx.tx.TransactionManager;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Thin set wrapper backed by the transactional map so set mutation follows the same staging rules.
 */
public final class TransactionalSet<V> {

    private final TransactionalMap<V, Boolean> delegate;

    public TransactionalSet(TransactionManager transactions) {
        this.delegate = new TransactionalMap<>(transactions);
    }

    public boolean add(V value) {
        boolean existed = delegate.containsKey(value);
        delegate.put(value, Boolean.TRUE);
        return !existed;
    }

    public boolean remove(V value) {
        boolean existed = delegate.containsKey(value);
        delegate.remove(value);
        return existed;
    }

    public boolean contains(V value) {
        return delegate.containsKey(value);
    }

    public Set<V> snapshotCommitted() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(delegate.snapshotCommitted().keySet()));
    }
}
