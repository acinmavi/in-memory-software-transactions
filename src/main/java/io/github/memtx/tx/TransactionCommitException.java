package io.github.memtx.tx;

/**
 * Signals a commit-time failure. Before-commit hook failures are safe to roll back; apply failures may be partial.
 */
public final class TransactionCommitException extends RuntimeException {

    public TransactionCommitException(String message, Throwable cause) {
        super(message, cause);
    }
}
