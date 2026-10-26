package ru.codex.codextest.model;


import java.util.Locale;

public enum AttachmentFileType {
    PNG, JPG, JPEG, TXT, LOG, PDF;

    public AttachmentMediaKind getMediaKind() {
        switch (this) {
            case JPG, PNG, JPEG -> {
                return AttachmentMediaKind.IMAGE;
            }
            default -> {
                return AttachmentMediaKind.DOCUMENT;
            }

        }
    }

    public String getContentType() {
        switch (this) {
            case PNG:
                return "image/png";
            case JPG, JPEG:
                return "image/jpeg";
            case LOG, TXT:
                return "text/plain";
            case PDF:
                return "application/pdf";
            default:
                throw new IllegalStateException("Неизвестный тип вложения: " + this);
        }
    }

    public static AttachmentFileType fromExtension(String extension) {
        if (extension == null) {
            throw new IllegalArgumentException("Расширение не указано");
        }
        try {
            return AttachmentFileType.valueOf(extension.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Формат файла не поддерживается");
        }
    }

    public static AttachmentFileType fromFilename(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Название отсутствует");
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("Некорректный файл");
        }
        String extension = filename.substring(dotIndex + 1);
        return fromExtension(extension);
    }
}
