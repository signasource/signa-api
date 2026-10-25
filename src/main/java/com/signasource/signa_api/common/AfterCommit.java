package com.signasource.signa_api.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs side effects (emails) only once the surrounding transaction commits, so a rollback never
 * leaves a message about something that did not happen. Outside a transaction it runs right away.
 */
public final class AfterCommit {

    private AfterCommit() {}

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }
}
