package ru.codex.codextest.service;

import org.springframework.web.multipart.MultipartFile;

public class AttachmentValidator {

    public void validateNotEmpty(MultipartFile file) {
        if (file == null) {
            throw new IllegalArgumentException("Файл не передан");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }
    }
}
