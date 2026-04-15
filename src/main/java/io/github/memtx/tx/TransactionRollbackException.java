package io.github.memtx.tx;

/**
 * Signals that rollback hook processing failed after staged state was discarded.
 */
public final class TransactionRollbackException extends RuntimeException {

    public TransactionRollbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
