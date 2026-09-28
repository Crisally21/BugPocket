package ru.codex.codextest.dto;

import ru.codex.codextest.model.AttachmentFileType;
import ru.codex.codextest.model.AttachmentMediaKind;
import ru.codex.codextest.model.BugAttachment;

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
    public static AttachmentResponse from(BugAttachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getMediaKind(),
                attachment.getFileType(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt()
        );
    }
}
