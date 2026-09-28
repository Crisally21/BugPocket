package ru.codex.codextest;

import org.junit.jupiter.api.Test;
import ru.codex.codextest.dto.AttachmentResponse;
import ru.codex.codextest.model.AttachmentFileType;
import ru.codex.codextest.model.AttachmentMediaKind;
import ru.codex.codextest.model.BugAttachment;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentResponseTests {
    @Test
    void copiesPublicAttachmentFieldsWithoutStorageKeys() {
        Instant createdAt = Instant.parse("2026-09-28T08:00:00Z");
        BugAttachment attachment = new BugAttachment();
        attachment.setId(17L);
        attachment.setOriginalFilename("отчёт.pdf");
        attachment.setMediaKind(AttachmentMediaKind.DOCUMENT);
        attachment.setFileType(AttachmentFileType.PDF);
        attachment.setContentType("application/pdf");
        attachment.setSizeBytes(2048L);
        attachment.setCreatedAt(createdAt);
        attachment.setStorageKey("internal-storage-key");
        attachment.setPreviewKey("internal-preview-key");

        AttachmentResponse response = AttachmentResponse.from(attachment);

        assertEquals(17L, response.id());
        assertEquals("отчёт.pdf", response.originalFilename());
        assertEquals(AttachmentMediaKind.DOCUMENT, response.mediaKind());
        assertEquals(AttachmentFileType.PDF, response.fileType());
        assertEquals("application/pdf", response.contentType());
        assertEquals(2048L, response.sizeBytes());
        assertEquals(createdAt, response.createdAt());
    }
}
