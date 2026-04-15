package io.github.memtx.resource;

import io.github.memtx.tx.TransactionManager;
import io.github.memtx.tx.TransactionParticipant;

/**
 * Scalar transactional wrapper for counters, pointers, or small coordination flags.
 */
public final class TransactionalReference<T> implements TransactionParticipant<TransactionalReference.Stage<T>> {

    private final Object monitor = new Object();
    private final TransactionManager transactions;
    private T committed;

    public TransactionalReference(TransactionManager transactions, T initialValue) {
        this.transactions = transactions;
        this.committed = initialValue;
    }

    public T get() {
        Stage<T> state = transactions.stateIfPresent(this);
        if (state != null && state.dirty) {
            return state.value;
        }
        synchronized (monitor) {
            return committed;
        }
    }

    public void set(T value) {
        if (!transactions.hasCurrentTransaction()) {
            synchronized (monitor) {
                committed = value;
            }
            return;
        }

        Stage<T> state = transactions.stateFor(this);
        state.dirty = true;
        state.value = value;
    }

    public T committedValue() {
        synchronized (monitor) {
            return committed;
        }
    }

    @Override
    public Stage<T> createState() {
        return new Stage<>();
    }

    @Override
    public void commit(Stage<T> state) {
        if (!state.dirty) {
            return;
        }
        synchronized (monitor) {
            committed = state.value;
        }
    }

    static final class Stage<T> {
        private boolean dirty;
        private T value;
    }
}
