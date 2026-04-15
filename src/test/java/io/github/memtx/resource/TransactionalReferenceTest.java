package io.github.memtx.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.memtx.tx.TransactionManager;
import org.junit.jupiter.api.Test;

class TransactionalReferenceTest {

    @Test
    void referenceValueCommitsAtomically() {
        TransactionManager manager = new TransactionManager();
        TransactionalReference<String> reference = new TransactionalReference<>(manager, "cold");

        manager.required(() -> {
            reference.set("warm");
            assertThat(reference.get()).isEqualTo("warm");
            assertThat(reference.committedValue()).isEqualTo("cold");
        });

        assertThat(reference.committedValue()).isEqualTo("warm");
    }
}
