package ru.codex.codextest.model;


import java.util.Locale;

public enum AttachmentFileType {
    PNG, JPG, JPEG, MP4, MOV, MKV, AVI, WEBM;

    public AttachmentMediaKind getMediaKind() {
        switch (this) {
            case JPG, PNG, JPEG -> {
                return AttachmentMediaKind.IMAGE;
            }
            default -> {
                return AttachmentMediaKind.VIDEO;
            }

        }
    }

    public String getContentType() {
        switch (this) {
            case PNG:
                return "image/png";
            case JPG, JPEG:
                return "image/jpeg";
            case MP4:
                return "video/mp4";
            case MOV:
                return "video/quicktime";
            case MKV:
                return "video/x-matroska";
            case AVI:
                return "video/x-msvideo";
            default:
                return "video/webm";
        }
    }

    public static AttachmentFileType fromExtension(String extension) {
        if (extension == null) {
            throw new IllegalArgumentException("Расширение не указано");
        }
        return AttachmentFileType.valueOf(extension.toUpperCase(Locale.ROOT));
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
