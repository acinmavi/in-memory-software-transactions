package io.github.memtx.tx;

/**
 * A transactional resource owns its staged state and knows how to publish it when the transaction commits.
 *
 * @param <S> staged state type used during the transaction
 */
public interface TransactionParticipant<S> {

    S createState();

    void commit(S state);
}
