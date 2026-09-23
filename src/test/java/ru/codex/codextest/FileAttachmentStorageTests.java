package ru.codex.codextest;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.codex.codextest.service.FileAttachmentStorage;

import static org.assertj.core.api.Assertions.*;

class FileAttachmentStorageTests {
    @TempDir Path temporaryDirectory;

    @Test
    void roundTripIndependentKeysAndDelete() throws Exception {
        Path root = temporaryDirectory.resolve("storage");
        var storage = new FileAttachmentStorage(root.toString());
        byte[] bytes = {0, 1, 2, (byte) 255};
        String first = storage.store(new ByteArrayInputStream(bytes));
        String second = storage.store(new ByteArrayInputStream(bytes));
        assertThat(first).isNotEqualTo(second);
        try (InputStream input = storage.open(first)) {
            assertThat(input.readAllBytes()).containsExactly(bytes);
        }
        storage.delete(first);
        storage.delete(first);
        assertThat(Files.exists(root.resolve(first))).isFalse();
        assertThat(Files.exists(root.resolve(second))).isTrue();
        assertThatThrownBy(() -> storage.open(first)).isInstanceOf(IOException.class);
    }

    @Test
    void rejectsPathsInsteadOfKeysAndMissingFiles() {
        var storage = new FileAttachmentStorage(temporaryDirectory.toString());
        for (String key : new String[]{"../outside", "C:\\outside", "/tmp/outside", "a/b", ""}) {
            assertThatThrownBy(() -> storage.open(key)).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(IOException.class);
        }
        assertThatThrownBy(() -> storage.open(UUID.randomUUID().toString())).isInstanceOf(IOException.class);
    }

    @Test
    void failedWriteRemovesPartialFile() throws Exception {
        var storage = new FileAttachmentStorage(temporaryDirectory.toString());
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("Simulated read failure"); }
        };
        assertThatThrownBy(() -> storage.store(broken)).isInstanceOf(IOException.class);
        try (var files = Files.list(temporaryDirectory)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void unusableRootReportsFailure() throws Exception {
        Path file = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(file, "file");
        var storage = new FileAttachmentStorage(file.toString());
        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(IOException.class);
    }
}
