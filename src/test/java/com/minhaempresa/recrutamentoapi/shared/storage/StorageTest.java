package com.minhaempresa.recrutamentoapi.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhaempresa.recrutamentoapi.support.SampleFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {

    @TempDir
    Path root;

    private LocalStorageService storage() {
        return new LocalStorageService(new StorageProperties(StorageProperties.Type.LOCAL, new StorageProperties.Local(root.toString()), null));
    }

    @Test
    void keyIsGeneratedFromCategoryUuidAndDetectedType() throws Exception {
        StoredFile stored = storage().store("company-logo", FileType.PNG, new byte[] {1, 2, 3});

        assertThat(stored.key()).matches("company-logo/[0-9a-f-]{36}\\.png");
        assertThat(stored.sizeBytes()).isEqualTo(3);
        assertThat(stored.sha256()).hasSize(64);
        assertThat(Files.readAllBytes(root.resolve(stored.key()))).containsExactly(1, 2, 3);
        assertThat(storage().load(stored.key()).contentLength()).isEqualTo(3);
    }

    @Test
    void keysCannotEscapeTheRoot() {
        LocalStorageService storage = storage();

        assertThatThrownBy(() -> storage.load("../outside.txt")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.load("company-logo/../../outside.txt")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store("../logo", FileType.PNG, new byte[] {1})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingFileAndIdempotentDelete() {
        LocalStorageService storage = storage();
        StoredFile stored = storage.store("company-logo", FileType.JPEG, new byte[] {9});

        storage.delete(stored.key());
        storage.delete(stored.key());

        assertThatThrownBy(() -> storage.load(stored.key())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void typeIsDetectedFromTheContent() {
        assertThat(FileType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0})).contains(FileType.PNG);
        assertThat(FileType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).contains(FileType.JPEG);
        assertThat(FileType.detect("RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).contains(FileType.WEBP);
        assertThat(FileType.detect("<svg></svg>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileType.detect(SampleFiles.pdf())).contains(FileType.PDF);
        assertThat(FileType.detect(new byte[0])).isEmpty();
    }

    @Test
    void wordDocumentsOnlyWithoutMacrosAndNeverTheLegacyFormat() {
        assertThat(FileType.detect(SampleFiles.docx())).contains(FileType.DOCX);
        assertThat(FileType.detect(SampleFiles.docxWithMacros())).isEmpty();
        assertThat(FileType.detect(SampleFiles.plainZip())).isEmpty();
        assertThat(FileType.detect(SampleFiles.legacyDoc())).isEmpty();
        // A ZIP signature followed by garbage is not an archive
        assertThat(FileType.detect(new byte[] {'P', 'K', 3, 4, 1, 2, 3})).isEmpty();
    }
}
