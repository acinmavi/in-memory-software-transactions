package io.github.memtx.tx;

/**
 * Public transaction view exposed to application code for lifecycle callback registration.
 */
public interface MemoryTransaction {

    void beforeCommit(Runnable callback);

    void afterCommit(Runnable callback);

    void afterRollback(Runnable callback);
}
