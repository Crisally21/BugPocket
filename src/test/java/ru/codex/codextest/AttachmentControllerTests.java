package ru.codex.codextest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import ru.codex.codextest.controller.AttachmentController;
import ru.codex.codextest.dto.AttachmentResponse;
import ru.codex.codextest.model.BugAttachment;
import ru.codex.codextest.service.AttachmentBatchResponse;
import ru.codex.codextest.service.AttachmentService;
import ru.codex.codextest.service.AttachmentStorage;
import ru.codex.codextest.model.AttachmentFileType;
import ru.codex.codextest.model.AttachmentMediaKind;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class AttachmentControllerTests {
    private final AttachmentService service = mock(AttachmentService.class);
    private final AttachmentStorage storage = mock(AttachmentStorage.class);
    private final AttachmentController controller = new AttachmentController(service, storage);

    @Test
    void listsAttachmentsForBug() {
        AttachmentResponse response = new AttachmentResponse(7L, "notes.txt", AttachmentMediaKind.DOCUMENT,
                AttachmentFileType.TXT, "text/plain", 5L, Instant.now());
        when(service.findByBugId(42L)).thenReturn(List.of(response));

        assertEquals(List.of(response), controller.list(42L));
    }

    @Test
    void passesMultipartFilesToService() {
        var file = new MockMultipartFile("files", "notes.txt", "text/plain", "hello".getBytes());
        var result = new AttachmentBatchResponse(List.of(), List.of());
        when(service.upload(eq(42L), anyList())).thenReturn(result);

        assertSame(result, controller.upload(42L, List.of(file)));
        verify(service).upload(42L, List.of(file));
    }

    @Test
    void downloadsOriginalWithSafeFilename() throws Exception {
        BugAttachment attachment = attachment("notes\".txt", "text/plain", "original", null);
        when(service.findById(42L, 7L)).thenReturn(attachment);
        when(storage.open("original")).thenReturn(new ByteArrayInputStream(new byte[]{1, 2}));

        var response = controller.download(42L, 7L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("attachment; filename=\"notes_.txt\"", response.getHeaders().getFirst("Content-Disposition"));
        assertEquals("text/plain", response.getHeaders().getContentType().toString());
        assertArrayEquals(new byte[]{1, 2}, response.getBody().getInputStream().readAllBytes());
    }

    @Test
    void contentUsesVideoPreviewWhenPresent() throws Exception {
        BugAttachment attachment = attachment("clip.mp4", "video/mp4", "original", "preview");
        when(service.findById(42L, 7L)).thenReturn(attachment);
        when(storage.open("preview")).thenReturn(new ByteArrayInputStream(new byte[]{3}));

        var response = controller.content(42L, 7L);

        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertArrayEquals(new byte[]{3}, response.getBody().getInputStream().readAllBytes());
        verify(storage).open("preview");
    }

    @Test
    void deletesAttachmentAndReturnsNoContent() throws Exception {
        assertEquals(HttpStatus.NO_CONTENT, controller.delete(42L, 7L).getStatusCode());
        verify(service).delete(42L, 7L);
    }

    private static BugAttachment attachment(String filename, String contentType,
                                            String storageKey, String previewKey) {
        BugAttachment attachment = new BugAttachment();
        attachment.setId(7L);
        attachment.setBugId(42L);
        attachment.setOriginalFilename(filename);
        attachment.setContentType(contentType);
        attachment.setStorageKey(storageKey);
        attachment.setPreviewKey(previewKey);
        return attachment;
    }
}
