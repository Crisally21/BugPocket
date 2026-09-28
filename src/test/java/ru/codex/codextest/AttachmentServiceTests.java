package ru.codex.codextest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.codex.codextest.exception.BugNotFoundException;
import ru.codex.codextest.exception.AttachmentNotFoundException;
import ru.codex.codextest.dto.AttachmentResponse;
import ru.codex.codextest.model.BugAttachment;
import ru.codex.codextest.repository.AttachmentRepository;
import ru.codex.codextest.repository.BugRepository;
import ru.codex.codextest.service.AttachmentService;
import ru.codex.codextest.service.AttachmentStorage;
import ru.codex.codextest.service.AttachmentValidator;
import ru.codex.codextest.service.VideoPreviewGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttachmentServiceTests {
    private final BugRepository bugs = mock(BugRepository.class);
    private final AttachmentRepository attachments = mock(AttachmentRepository.class);
    private final AttachmentService service = new AttachmentService(bugs, attachments);

    @Test
    void returnsAttachmentBelongingToRequestedBug() {
        BugAttachment attachment = new BugAttachment();
        attachment.setId(7L);
        attachment.setBugId(42L);
        when(bugs.existsById(42L)).thenReturn(true);
        when(attachments.findByIdAndBugId(7L, 42L)).thenReturn(Optional.of(attachment));

        assertSame(attachment, service.findById(42L, 7L));
        verify(attachments).findByIdAndBugId(7L, 42L);
        verifyNoMoreInteractions(attachments);
    }

    @Test
    void rejectsAttachmentAbsentFromRequestedBug() {
        // Репозиторий возвращает empty и для отсутствующего вложения, и для чужого бага.
        when(bugs.existsById(42L)).thenReturn(true);
        when(attachments.findByIdAndBugId(7L, 42L)).thenReturn(Optional.empty());

        AttachmentNotFoundException error = assertThrows(AttachmentNotFoundException.class,
                () -> service.findById(42L, 7L));

        assertEquals("Вложение с номером 7 не найдено", error.getMessage());
        verify(attachments).findByIdAndBugId(7L, 42L);
        verifyNoMoreInteractions(attachments);
    }

    @Test
    void rejectsSingleAttachmentLookupForMissingBugBeforeQueryingAttachments() {
        when(bugs.existsById(42L)).thenReturn(false);

        BugNotFoundException error = assertThrows(BugNotFoundException.class,
                () -> service.findById(42L, 7L));

        assertEquals("Баг с номером 42 не найден", error.getMessage());
        verifyNoInteractions(attachments);
    }

    @Test
    void returnsAttachmentsForRequestedBugInRepositoryOrder() {
        long bugId = 42L;
        BugAttachment first = new BugAttachment();
        first.setId(7L);
        first.setBugId(bugId);
        BugAttachment second = new BugAttachment();
        second.setId(9L);
        second.setBugId(bugId);
        when(bugs.existsById(bugId)).thenReturn(true);
        when(attachments.findByBugIdOrderByCreatedAtAscIdAsc(bugId)).thenReturn(List.of(first, second));

        assertEquals(List.of(AttachmentResponse.from(first), AttachmentResponse.from(second)), service.findByBugId(bugId));
        verify(attachments).findByBugIdOrderByCreatedAtAscIdAsc(bugId);
    }

    @Test
    void returnsEmptyListForExistingBugWithoutAttachments() {
        when(bugs.existsById(42L)).thenReturn(true);
        when(attachments.findByBugIdOrderByCreatedAtAscIdAsc(42L)).thenReturn(List.of());

        assertTrue(service.findByBugId(42L).isEmpty());
        verify(attachments).findByBugIdOrderByCreatedAtAscIdAsc(42L);
    }

    @Test
    void rejectsMissingBugWithoutQueryingAttachments() {
        when(bugs.existsById(42L)).thenReturn(false);

        BugNotFoundException error = assertThrows(BugNotFoundException.class, () -> service.findByBugId(42L));

        assertEquals("Баг с номером 42 не найден", error.getMessage());
        verifyNoInteractions(attachments);
    }

    @Test
    void acceptsValidFilesAndReportsInvalidFilesInSameBatch() throws Exception {
        AttachmentStorage storage = mock(AttachmentStorage.class);
        VideoPreviewGenerator previews = mock(VideoPreviewGenerator.class);
        AttachmentService uploadService = new AttachmentService(
                bugs, attachments, new AttachmentValidator(), storage, previews);
        when(bugs.existsById(42L)).thenReturn(true);
        when(attachments.totalSizeByBugId(42L)).thenReturn(0L);
        when(storage.store(any(InputStream.class))).thenReturn("stored-key");
        when(attachments.saveAndFlush(any(BugAttachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = uploadService.upload(42L, List.of(
                new MockMultipartFile("files", "notes.txt", "text/plain", "hello".getBytes()),
                new MockMultipartFile("files", "malware.exe", "application/octet-stream", new byte[]{1})
        ));

        assertEquals(1, result.accepted().size());
        assertEquals(1, result.errors().size());
        assertEquals("malware.exe", result.errors().get(0).filename());
        verify(attachments).saveAndFlush(any(BugAttachment.class));
    }

    @Test
    void rejectsWholeValidBatchWhenRemainingQuotaIsTooSmall() {
        AttachmentStorage storage = mock(AttachmentStorage.class);
        AttachmentService uploadService = new AttachmentService(
                bugs, attachments, new AttachmentValidator(), storage, mock(VideoPreviewGenerator.class));
        when(bugs.existsById(42L)).thenReturn(true);
        when(attachments.totalSizeByBugId(42L)).thenReturn(24_999_999L);

        var result = uploadService.upload(42L, List.of(
                new MockMultipartFile("files", "one.txt", "text/plain", new byte[]{1, 2}),
                new MockMultipartFile("files", "two.txt", "text/plain", new byte[]{3})
        ));

        assertTrue(result.accepted().isEmpty());
        assertEquals(2, result.errors().size());
        verifyNoInteractions(storage);
        verify(attachments, never()).saveAndFlush(any());
    }

    @Test
    void removesStoredOriginalWhenVideoPreviewGenerationFails() throws Exception {
        AttachmentStorage storage = mock(AttachmentStorage.class);
        VideoPreviewGenerator previews = mock(VideoPreviewGenerator.class);
        AttachmentService uploadService = new AttachmentService(
                bugs, attachments, new AttachmentValidator(), storage, previews);
        when(bugs.existsById(42L)).thenReturn(true);
        when(attachments.totalSizeByBugId(42L)).thenReturn(0L);
        when(storage.store(any(InputStream.class))).thenReturn("video-key");
        when(previews.generatePreview(any(InputStream.class)))
                .thenThrow(new IOException("preview failed"));

        var result = uploadService.upload(42L, List.of(
                new MockMultipartFile("files", "clip.mp4", "video/mp4", new byte[]{1, 2, 3})
        ));

        assertTrue(result.accepted().isEmpty());
        assertEquals("clip.mp4", result.errors().get(0).filename());
        verify(storage).delete("video-key");
        verify(attachments, never()).saveAndFlush(any());
    }

    @Test
    void deletesDatabaseRecordAndBothStoredFiles() throws Exception {
        AttachmentStorage storage = mock(AttachmentStorage.class);
        AttachmentService deleteService = new AttachmentService(
                bugs, attachments, new AttachmentValidator(), storage, mock(VideoPreviewGenerator.class));
        BugAttachment attachment = new BugAttachment();
        attachment.setStorageKey("original-key");
        attachment.setPreviewKey("preview-key");
        when(bugs.existsById(42L)).thenReturn(true);
        when(attachments.findByIdAndBugId(7L, 42L)).thenReturn(Optional.of(attachment));

        deleteService.delete(42L, 7L);

        verify(attachments).delete(attachment);
        verify(storage).delete("original-key");
        verify(storage).delete("preview-key");
    }
}
