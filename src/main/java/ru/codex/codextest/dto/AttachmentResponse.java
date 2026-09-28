package ru.codex.codextest.dto;

import ru.codex.codextest.model.AttachmentFileType;
import ru.codex.codextest.model.AttachmentMediaKind;

import java.time.Instant;

public record AttachmentResponse(
        long id,
        String originalFilename,
        AttachmentMediaKind mediaKind,
        AttachmentFileType fileType,
        String contentType,
        long sizeBytes,
        Instant createdAt
) {
}
