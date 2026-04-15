package io.github.memtx.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.memtx.tx.TransactionManager;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransactionalSetTest {

    @Test
    void addAndRemoveStayTransactionalUntilCommit() {
        TransactionManager manager = new TransactionManager();
        TransactionalSet<String> set = new TransactionalSet<>(manager);
        set.add("seed");

        manager.required(() -> {
            set.add("alpha");
            set.remove("seed");

            assertThat(set.contains("alpha")).isTrue();
            assertThat(set.contains("seed")).isFalse();
            assertThat(set.snapshotCommitted()).containsExactly("seed");
        });

        assertThat(set.snapshotCommitted()).containsExactly("alpha");
    }
}
