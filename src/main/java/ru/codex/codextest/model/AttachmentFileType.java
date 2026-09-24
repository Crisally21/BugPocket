package ru.codex.codextest.model;

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
}
