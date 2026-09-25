package ru.codex.codextest.service;

import org.springframework.web.multipart.MultipartFile;

public class AttachmentValidator {

    private static final long MAX_FILE_SIZE_BYTES = 25000000L;

    public void validateNotEmpty(MultipartFile file) {
        if (file == null) {
            throw new IllegalArgumentException("Файл не передан");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }
    }

    public void validateSize(MultipartFile file) {
        validateNotEmpty(file);
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Размер файла превышает 25 MB");
        }
        
    }
}
