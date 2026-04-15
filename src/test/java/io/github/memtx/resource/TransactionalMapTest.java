package io.github.memtx.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.memtx.tx.TransactionManager;
import org.junit.jupiter.api.Test;

class TransactionalMapTest {

    @Test
    void stagedWritesAreVisibleInsideTransactionButHiddenOutsideUntilCommit() {
        TransactionManager manager = new TransactionManager();
        TransactionalMap<String, Integer> map = new TransactionalMap<>(manager);
        map.put("base", 1);

        manager.required(() -> {
            map.put("base", 2);
            map.put("new", 3);

            assertThat(map.get("base")).isEqualTo(2);
            assertThat(map.get("new")).isEqualTo(3);
            assertThat(map.snapshotCommitted()).containsEntry("base", 1).hasSize(1);
        });

        assertThat(map.snapshotCommitted()).containsEntry("base", 2).containsEntry("new", 3).hasSize(2);
    }

    @Test
    void rollbackDiscardsClearRemoveAndPutOperations() {
        TransactionManager manager = new TransactionManager();
        TransactionalMap<String, Integer> map = new TransactionalMap<>(manager);
        map.put("a", 1);
        map.put("b", 2);

        assertThatThrownBy(() -> manager.required(() -> {
            map.clear();
            map.put("c", 3);
            map.remove("c");
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(map.snapshotCommitted()).containsEntry("a", 1).containsEntry("b", 2).hasSize(2);
    }
}
