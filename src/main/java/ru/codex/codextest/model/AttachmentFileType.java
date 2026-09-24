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
}
