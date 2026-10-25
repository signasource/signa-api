package com.signasource.signa_api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldRunRightAwayOutsideATransaction() {
        List<String> ran = new ArrayList<>();

        AfterCommit.run(() -> ran.add("sent"));

        assertEquals(List.of("sent"), ran);
    }

    @Test
    void shouldWaitForTheCommitInsideATransaction() {
        TransactionSynchronizationManager.initSynchronization();
        List<String> ran = new ArrayList<>();

        AfterCommit.run(() -> ran.add("sent"));
        assertEquals(List.of(), ran);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertEquals(List.of("sent"), ran);
    }
}
