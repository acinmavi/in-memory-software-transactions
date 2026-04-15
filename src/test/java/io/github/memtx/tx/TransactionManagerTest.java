package io.github.memtx.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.memtx.resource.TransactionalMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionManagerTest {

    @Test
    void commitPublishesChangesAndRunsCommitHooksInOrder() {
        TransactionManager manager = new TransactionManager();
        TransactionalMap<String, Integer> balances = new TransactionalMap<>(manager);
        List<String> events = new ArrayList<>();

        manager.required(() -> {
            balances.put("alice", 5);
            manager.current().beforeCommit(() -> events.add("before-1"));
            manager.current().beforeCommit(() -> events.add("before-2"));
            manager.current().afterCommit(() -> events.add("after-1"));
            manager.current().afterCommit(() -> events.add("after-2"));
        });

        assertThat(balances.get("alice")).isEqualTo(5);
        assertThat(events).containsExactly("before-1", "before-2", "after-1", "after-2");
    }

    @Test
    void rollbackDiscardsChangesAndRunsRollbackHooks() {
        TransactionManager manager = new TransactionManager();
        TransactionalMap<String, Integer> balances = new TransactionalMap<>(manager);
        List<String> events = new ArrayList<>();

        assertThatThrownBy(() -> manager.required(() -> {
            balances.put("alice", 5);
            manager.current().afterRollback(() -> events.add("rollback"));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(balances.containsKey("alice")).isFalse();
        assertThat(events).containsExactly("rollback");
    }

    @Test
    void requiredScopesShareOneUnderlyingTransaction() {
        TransactionManager manager = new TransactionManager();
        TransactionalMap<String, Integer> map = new TransactionalMap<>(manager);
        List<Integer> identities = new ArrayList<>();

        manager.required(() -> {
            identities.add(System.identityHashCode(manager.current()));
            map.put("outer", 1);
            manager.required(() -> {
                identities.add(System.identityHashCode(manager.current()));
                map.put("inner", 2);
            });
            assertThat(map.snapshotCommitted()).isEmpty();
        });

        assertThat(identities).hasSize(2);
        assertThat(identities.get(0)).isEqualTo(identities.get(1));
        assertThat(map.snapshotCommitted()).containsEntry("outer", 1).containsEntry("inner", 2);
    }

    @Test
    void requiresNewCommitsIndependentlyFromOuterRollback() {
        TransactionManager manager = new TransactionManager();
        TransactionalMap<String, Integer> map = new TransactionalMap<>(manager);

        assertThatThrownBy(() -> manager.required(() -> {
            map.put("outer", 1);
            manager.requiresNew(() -> map.put("inner", 2));
            throw new IllegalStateException("fail outer");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(map.snapshotCommitted()).containsEntry("inner", 2);
        assertThat(map.snapshotCommitted()).doesNotContainKey("outer");
    }

    @Test
    void supportsRunsWithoutAllocatingATransactionWhenNoneExists() {
        TransactionManager manager = new TransactionManager();
        TransactionalMap<String, Integer> map = new TransactionalMap<>(manager);

        manager.supports(() -> map.put("direct", 7));

        assertThat(map.get("direct")).isEqualTo(7);
        assertThat(manager.currentOrNull()).isNull();
        assertThat(manager.poolMetrics().createdCount()).isZero();
    }

    @Test
    void transactionInstancesAreReusedAfterReset() {
        TransactionManager manager = new TransactionManager(8);
        TransactionalMap<String, Integer> map = new TransactionalMap<>(manager);
        List<Integer> identities = new ArrayList<>();

        manager.required(() -> {
            identities.add(System.identityHashCode(manager.current()));
            map.put("first", 1);
        });

        manager.required(() -> {
            identities.add(System.identityHashCode(manager.current()));
            assertThat(map.containsKey("first")).isTrue();
            map.put("second", 2);
        });

        assertThat(identities.get(0)).isEqualTo(identities.get(1));
        assertThat(manager.poolMetrics().createdCount()).isEqualTo(1);
        assertThat(manager.poolMetrics().reusedCount()).isGreaterThanOrEqualTo(1);
    }
}
