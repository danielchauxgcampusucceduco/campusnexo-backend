package edu.campusnexo.infrastructure.adapter.out.persistence;

import edu.campusnexo.application.port.out.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adapter of {@link UnitOfWork} over Spring's transaction manager: the repositories' own
 * {@code @Transactional} methods join the transaction opened here, and after-commit actions are registered
 * as transaction synchronizations.
 */
@Component
class SpringUnitOfWork implements UnitOfWork {
    private final TransactionTemplate transactions;

    SpringUnitOfWork(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    @Override
    public void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
