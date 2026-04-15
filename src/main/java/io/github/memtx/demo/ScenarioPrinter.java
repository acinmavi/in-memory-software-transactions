package io.github.memtx.demo;

import io.github.memtx.resource.TransactionalMap;
import io.github.memtx.resource.TransactionalReference;
import io.github.memtx.resource.TransactionalSet;
import io.github.memtx.tx.TransactionManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Small scenario harness that prints committed and transactional views so behavior is obvious from one run.
 */
public final class ScenarioPrinter {

    private final TransactionManager transactions = new TransactionManager();
    private final TransactionalMap<String, Integer> counters = new TransactionalMap<>(transactions);
    private final TransactionalSet<String> activeKeys = new TransactionalSet<>(transactions);
    private final TransactionalReference<String> mode = new TransactionalReference<>(transactions, "IDLE");

    public void run() {
        commitScenario();
        rollbackScenario();
        propagationScenario();
        poolingScenario();
    }

    private void commitScenario() {
        List<String> events = new ArrayList<>();
        transactions.required(() -> {
            counters.put("jobs", 3);
            activeKeys.add("jobs");
            mode.set("COMMITTING");
            transactions.current().beforeCommit(() -> events.add("before-commit"));
            transactions.current().afterCommit(() -> events.add("after-commit"));
            print("commit / inside tx", "jobs=" + counters.get("jobs") + ", mode=" + mode.get());
        });

        print("commit / committed", counters.snapshotCommitted() + ", active=" + activeKeys.snapshotCommitted() + ", mode=" + mode.committedValue());
        print("commit / hooks", events.toString());
    }

    private void rollbackScenario() {
        List<String> events = new ArrayList<>();
        try {
            transactions.required(() -> {
                counters.put("jobs", 99);
                activeKeys.add("rollback-only");
                mode.set("FAILED");
                transactions.current().afterRollback(() -> events.add("after-rollback"));
                print("rollback / inside tx", "jobs=" + counters.get("jobs") + ", mode=" + mode.get());
                throw new IllegalStateException("validation failed");
            });
        } catch (IllegalStateException ignored) {
            // Demo intentionally swallows the error after proving rollback semantics.
        }

        print("rollback / committed", counters.snapshotCommitted() + ", active=" + activeKeys.snapshotCommitted() + ", mode=" + mode.committedValue());
        print("rollback / hooks", events.toString());
    }

    private void propagationScenario() {
        try {
            transactions.required(() -> {
                counters.put("outer", 1);
                transactions.requiresNew(() -> counters.put("inner", 2));
                throw new IllegalStateException("outer failed");
            });
        } catch (IllegalStateException ignored) {
            // Expected for the demonstration.
        }

        print("propagation / committed", counters.snapshotCommitted().toString());
    }

    private void poolingScenario() {
        transactions.required(() -> counters.put("pool-a", 10));
        transactions.required(() -> counters.put("pool-b", 20));
        print("pool / metrics", "created=" + transactions.poolMetrics().createdCount()
            + ", reused=" + transactions.poolMetrics().reusedCount()
            + ", pooled=" + transactions.poolMetrics().pooledCount());
    }

    private void print(String label, String value) {
        System.out.println(String.format("%-24s %s", label, value));
    }
}
