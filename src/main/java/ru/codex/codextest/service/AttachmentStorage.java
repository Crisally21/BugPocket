package ru.codex.codextest.service;

import java.io.IOException;
import java.io.InputStream;

public interface AttachmentStorage {
    String store(InputStream input) throws IOException;
    InputStream open(String key) throws IOException;
    void delete(String key) throws IOException;
}
