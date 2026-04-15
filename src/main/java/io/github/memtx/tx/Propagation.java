package io.github.memtx.tx;

/**
 * Propagation controls how a call interacts with a transaction already bound to the current thread.
 */
public enum Propagation {
    SUPPORTS,
    REQUIRED,
    REQUIRES_NEW
}
