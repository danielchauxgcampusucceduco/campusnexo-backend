package edu.campusnexo.application.port.out;

import java.util.function.Supplier;

/**
 * Transaction boundary of a use case. Everything done inside {@link #inTransaction} is committed together
 * or not at all; {@link #afterCommit} defers an effect (notifying observers, for example) until the data it
 * talks about is really stored. The application owns this port so the use cases, not the repositories,
 * decide what is atomic.
 */
public interface UnitOfWork {
    <T> T inTransaction(Supplier<T> work);

    /** Runs {@code action} after the current transaction commits, or right away when there is none. */
    void afterCommit(Runnable action);

    /** No transactions: for tools and in-memory tests, where each repository call is already atomic. */
    static UnitOfWork direct() {
        return new UnitOfWork() {
            @Override
            public <T> T inTransaction(Supplier<T> work) {
                return work.get();
            }

            @Override
            public void afterCommit(Runnable action) {
                action.run();
            }
        };
    }
}
