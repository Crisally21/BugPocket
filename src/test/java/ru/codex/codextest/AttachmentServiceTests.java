package ru.codex.codextest;

import org.junit.jupiter.api.Test;
import ru.codex.codextest.exception.BugNotFoundException;
import ru.codex.codextest.model.BugAttachment;
import ru.codex.codextest.repository.AttachmentRepository;
import ru.codex.codextest.repository.BugRepository;
import ru.codex.codextest.service.AttachmentService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttachmentServiceTests {
    private final BugRepository bugs = mock(BugRepository.class);
    private final AttachmentRepository attachments = mock(AttachmentRepository.class);
    private final AttachmentService service = new AttachmentService(bugs, attachments);

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

        assertEquals(List.of(first, second), service.findByBugId(bugId));
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
}
