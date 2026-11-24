package com.minhaempresa.recrutamentoapi.shared.storage;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Keeps the storage in step with the database: a file uploaded in a transaction that rolls back is
 * deleted, and a file whose record was deleted is removed only after the commit (never before, so a
 * rolled-back deletion keeps its file). Failures are logged; a periodic cleanup of orphans comes in
 * stage 15.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageCleanup {

    private final StorageService storage;

    /** Deletes {@code key} if the current transaction rolls back (call right after storing it). */
    public void deleteIfRolledBack(String key) {
        register(List.of(key), TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    /** Deletes the files once the current transaction has committed. */
    public void deleteAfterCommit(Collection<String> keys) {
        register(List.copyOf(keys), TransactionSynchronization.STATUS_COMMITTED);
    }

    private void register(List<String> keys, int whenStatus) {
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("StorageCleanup needs an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == whenStatus) {
                    keys.forEach(StorageCleanup.this::deleteQuietly);
                }
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete stored file {}", key, e);
        }
    }
}
