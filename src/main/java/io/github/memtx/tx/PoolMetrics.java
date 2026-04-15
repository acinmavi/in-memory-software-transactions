package io.github.memtx.tx;

/**
 * Immutable snapshot of pool activity. The demo uses this to show allocation behavior.
 */
public final class PoolMetrics {

    private final int createdCount;
    private final int reusedCount;
    private final int pooledCount;

    public PoolMetrics(int createdCount, int reusedCount, int pooledCount) {
        this.createdCount = createdCount;
        this.reusedCount = reusedCount;
        this.pooledCount = pooledCount;
    }

    public int createdCount() {
        return createdCount;
    }

    public int reusedCount() {
        return reusedCount;
    }

    public int pooledCount() {
        return pooledCount;
    }
}
