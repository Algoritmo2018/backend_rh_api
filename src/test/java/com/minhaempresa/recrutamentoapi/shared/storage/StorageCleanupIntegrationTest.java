package com.minhaempresa.recrutamentoapi.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.SampleFiles;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** The bucket follows the database: no orphan file after a rollback, no lost file before a commit. */
@IntegrationTest
class StorageCleanupIntegrationTest {

    @Autowired
    StorageService storage;

    @Autowired
    StorageCleanup cleanup;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void aFileUploadedInARolledBackTransactionIsDeleted() {
        String key = transactions.execute(status -> {
            StoredFile stored = storage.store("documents", FileType.PDF, SampleFiles.pdf());
            cleanup.deleteIfRolledBack(stored.key());
            status.setRollbackOnly();
            return stored.key();
        });
        assertThatThrownBy(() -> storage.load(key)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void aDeletionOnlyHappensWhenTheTransactionCommits() {
        String kept = storage.store("documents", FileType.PDF, SampleFiles.pdf()).key();
        transactions.executeWithoutResult(status -> {
            cleanup.deleteAfterCommit(List.of(kept));
            status.setRollbackOnly();
        });
        assertThat(storage.load(kept).exists()).isTrue();

        transactions.executeWithoutResult(status -> cleanup.deleteAfterCommit(List.of(kept)));
        assertThatThrownBy(() -> storage.load(kept)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void keysOutsideTheGeneratedFormatAreRefused() {
        assertThatThrownBy(() -> storage.load("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("documents/../../x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store("Docs/..", FileType.PDF, SampleFiles.pdf())).isInstanceOf(IllegalArgumentException.class);
    }
}
