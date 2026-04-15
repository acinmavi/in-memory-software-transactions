package io.github.memtx.tx;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A pooled transaction instance keeps staged resource state and hook lists in reusable containers.
 */
final class PooledTransaction implements MemoryTransaction {

    private final Map<TransactionParticipant<?>, Object> states = new IdentityHashMap<>();
    private final List<TransactionParticipant<?>> participants = new ArrayList<>();
    private final List<Runnable> beforeCommitHooks = new ArrayList<>();
    private final List<Runnable> afterCommitHooks = new ArrayList<>();
    private final List<Runnable> afterRollbackHooks = new ArrayList<>();

    private int depth;

    void enter() {
        depth += 1;
    }

    boolean isRootScope() {
        return depth == 1;
    }

    boolean isActive() {
        return depth > 0;
    }

    void commitBoundary() {
        depth -= 1;
        if (depth > 0) {
            return;
        }

        try {
            runHooks(beforeCommitHooks);
        } catch (RuntimeException failure) {
            rollbackStage();
            throw new TransactionCommitException("before-commit hook failed", failure);
        }

        try {
            for (TransactionParticipant<?> participant : participants) {
                commitParticipant(participant);
            }
        } catch (RuntimeException failure) {
            throw new TransactionCommitException(
                "commit failed after staged changes started applying; consistency now depends on participant behavior",
                failure
            );
        }

        try {
            runHooks(afterCommitHooks);
        } catch (RuntimeException failure) {
            throw new TransactionCommitException("after-commit hook failed after state was already published", failure);
        }
    }

    void rollbackBoundary() {
        depth -= 1;
        if (depth > 0) {
            return;
        }

        rollbackStage();
    }

    private void rollbackStage() {
        try {
            runHooks(afterRollbackHooks);
        } catch (RuntimeException failure) {
            throw new TransactionRollbackException("rollback hook failed after staged state was discarded", failure);
        }
    }

    <S> boolean hasState(TransactionParticipant<S> participant) {
        return states.containsKey(participant);
    }

    @SuppressWarnings("unchecked")
    <S> S getState(TransactionParticipant<S> participant) {
        return (S) states.get(participant);
    }

    @SuppressWarnings("unchecked")
    <S> S getOrCreateState(TransactionParticipant<S> participant) {
        Object existing = states.get(participant);
        if (existing != null || states.containsKey(participant)) {
            return (S) existing;
        }

        S created = participant.createState();
        states.put(participant, created);
        participants.add(participant);
        return created;
    }

    private void runHooks(List<Runnable> hooks) {
        for (Runnable hook : hooks) {
            hook.run();
        }
    }

    @SuppressWarnings("unchecked")
    private <S> void commitParticipant(TransactionParticipant<S> participant) {
        participant.commit((S) states.get(participant));
    }

    void reset() {
        depth = 0;
        states.clear();
        participants.clear();
        beforeCommitHooks.clear();
        afterCommitHooks.clear();
        afterRollbackHooks.clear();
    }

    @Override
    public void beforeCommit(Runnable callback) {
        beforeCommitHooks.add(callback);
    }

    @Override
    public void afterCommit(Runnable callback) {
        afterCommitHooks.add(callback);
    }

    @Override
    public void afterRollback(Runnable callback) {
        afterRollbackHooks.add(callback);
    }
}
