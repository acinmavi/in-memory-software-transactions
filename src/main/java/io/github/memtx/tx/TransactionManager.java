package io.github.memtx.tx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Thread-bound transaction manager for in-memory resources.
 *
 * <p>The manager keeps transaction ownership simple: one thread, one current transaction,
 * plus optional suspension when entering a {@code REQUIRES_NEW} scope.</p>
 */
public final class TransactionManager {

    private final ThreadLocal<PooledTransaction> current = new ThreadLocal<>();
    private final Deque<PooledTransaction> pool = new ArrayDeque<>();
    private final int maxPoolSize;

    private int createdCount;
    private int reusedCount;

    public TransactionManager() {
        this(32);
    }

    public TransactionManager(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public void required(Runnable action) {
        execute(Propagation.REQUIRED, () -> {
            action.run();
            return null;
        });
    }

    public void requiresNew(Runnable action) {
        execute(Propagation.REQUIRES_NEW, () -> {
            action.run();
            return null;
        });
    }

    public void supports(Runnable action) {
        execute(Propagation.SUPPORTS, () -> {
            action.run();
            return null;
        });
    }

    public <T> T execute(Propagation propagation, Supplier<T> supplier) {
        Objects.requireNonNull(propagation, "propagation");
        Objects.requireNonNull(supplier, "supplier");

        PooledTransaction existing = current.get();
        if (propagation == Propagation.SUPPORTS && existing == null) {
            return supplier.get();
        }

        if (propagation == Propagation.REQUIRED && existing != null) {
            existing.enter();
            try {
                T result = supplier.get();
                existing.commitBoundary();
                return result;
            } catch (RuntimeException | Error failure) {
                existing.rollbackBoundary();
                throw failure;
            }
        }

        if (propagation == Propagation.SUPPORTS && existing != null) {
            existing.enter();
            try {
                T result = supplier.get();
                existing.commitBoundary();
                return result;
            } catch (RuntimeException | Error failure) {
                existing.rollbackBoundary();
                throw failure;
            }
        }

        PooledTransaction transaction = borrow();
        PooledTransaction suspended = existing;
        current.set(transaction);
        transaction.enter();

        try {
            T result = supplier.get();
            transaction.commitBoundary();
            return result;
        } catch (RuntimeException | Error failure) {
            if (transaction.isActive()) {
                transaction.rollbackBoundary();
            }
            throw failure;
        } finally {
            current.set(suspended);
            release(transaction);
        }
    }

    public MemoryTransaction current() {
        PooledTransaction transaction = current.get();
        if (transaction == null) {
            throw new IllegalStateException("no transaction is currently bound to this thread");
        }
        return transaction;
    }

    public MemoryTransaction currentOrNull() {
        return current.get();
    }

    public <S> S stateFor(TransactionParticipant<S> participant) {
        return currentTransaction().getOrCreateState(participant);
    }

    public <S> S stateIfPresent(TransactionParticipant<S> participant) {
        PooledTransaction transaction = current.get();
        if (transaction == null || !transaction.hasState(participant)) {
            return null;
        }
        return transaction.getState(participant);
    }

    public boolean hasCurrentTransaction() {
        return current.get() != null;
    }

    public PoolMetrics poolMetrics() {
        synchronized (pool) {
            return new PoolMetrics(createdCount, reusedCount, pool.size());
        }
    }

    private PooledTransaction currentTransaction() {
        PooledTransaction transaction = current.get();
        if (transaction == null) {
            throw new IllegalStateException("transactional state can only be staged inside a transaction");
        }
        return transaction;
    }

    private PooledTransaction borrow() {
        synchronized (pool) {
            PooledTransaction transaction = pool.pollFirst();
            if (transaction == null) {
                createdCount += 1;
                return new PooledTransaction();
            }

            reusedCount += 1;
            return transaction;
        }
    }

    private void release(PooledTransaction transaction) {
        transaction.reset();
        synchronized (pool) {
            if (pool.size() < maxPoolSize) {
                pool.addFirst(transaction);
            }
        }
    }
}
