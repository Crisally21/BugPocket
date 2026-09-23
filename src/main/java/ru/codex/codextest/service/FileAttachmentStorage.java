package ru.codex.codextest.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileAttachmentStorage implements AttachmentStorage {
    private final Path configuredRoot;
    private Path root;

    public FileAttachmentStorage(@Value("${bugpocket.attachments.storage-root:./data/attachments}") String root) {
        configuredRoot = Path.of(root).toAbsolutePath().normalize();
    }

    private synchronized Path root() throws IOException {
        if (root == null) {
            Files.createDirectories(configuredRoot);
            root = configuredRoot.toRealPath();
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root)) {
            throw new IOException("Attachment storage root is unavailable");
        }
        return root;
    }

    private Path resolve(String key) throws IOException {
        if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IOException("Invalid attachment storage key");
        }
        return root().resolve(key);
    }

    @Override
    public String store(InputStream input) throws IOException {
        String key = UUID.randomUUID().toString();
        Path target = resolve(key);
        boolean created = false;
        try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            created = true;
            input.transferTo(output);
        } catch (IOException failure) {
            try {
                if (created) Files.deleteIfExists(target);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        return key;
    }

    @Override
    public InputStream open(String key) throws IOException {
        Path target = resolve(key);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || !target.toRealPath().getParent().equals(root())) {
            throw new IOException("Attachment file is unavailable");
        }
        return Files.newInputStream(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public void delete(String key) throws IOException {
        Path target = resolve(key);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    || !target.toRealPath().getParent().equals(root()))) {
            throw new IOException("Invalid attachment file");
        }
        Files.deleteIfExists(target);
    }
}
