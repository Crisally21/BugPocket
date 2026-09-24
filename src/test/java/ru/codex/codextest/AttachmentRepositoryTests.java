package ru.codex.codextest;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import ru.codex.codextest.model.*;
import ru.codex.codextest.repository.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class AttachmentRepositoryTests {
    @Autowired AttachmentRepository attachments;
    @Autowired BugRepository bugs;
    @Autowired
    EntityManager entityManager;

    private Bug bug() {
        Bug bug = new Bug();
        bug.setHeader("Repository test");
        return bugs.saveAndFlush(bug);
    }

    private BugAttachment attachment(long bugId, long size) {
        BugAttachment attachment = new BugAttachment();
        attachment.setBugId(bugId);
        attachment.setMediaKind(AttachmentMediaKind.IMAGE);
        attachment.setFileType(AttachmentFileType.PNG);
        attachment.setContentType("image/png");
        attachment.setOriginalFilename("Скриншот.png");
        attachment.setStorageKey(UUID.randomUUID().toString());
        attachment.setSizeBytes(size);
        return attachments.saveAndFlush(attachment);
    }

    @Test
    void metadataQueriesAndMutationsDoNotTouchParentTimestamp() {
        Bug parent = bug();
        entityManager.clear();
        parent = bugs.findById(parent.getId()).orElseThrow();
        var timestamp = parent.getUpdatedAt();
        assertThat(attachments.totalSizeByBugId(parent.getId())).isZero();
        assertThat(attachments.findByBugIdOrderByCreatedAtAscIdAsc(parent.getId())).isEmpty();
        BugAttachment first = attachment(parent.getId(), 10);
        BugAttachment second = attachment(parent.getId(), 20);
        entityManager.clear();
        assertThat(bugs.findById(parent.getId()).orElseThrow().getUpdatedAt()).isEqualTo(timestamp);
        assertThat(attachments.countByBugId(parent.getId())).isEqualTo(2);
        assertThat(attachments.totalSizeByBugId(parent.getId())).isEqualTo(30);
        assertThat(attachments.findByBugIdOrderByCreatedAtAscIdAsc(parent.getId()))
                .extracting(BugAttachment::getId).containsExactly(first.getId(), second.getId());
        assertThat(first.getOriginalFilename()).isEqualTo(second.getOriginalFilename());
        assertThat(first.getStorageKey()).isNotEqualTo(second.getStorageKey());
        assertThat(first.getCreatedAt()).isNotNull();
        assertThat(attachments.findByIdAndBugId(first.getId(), bug().getId())).isEmpty();
        attachments.delete(first);
        attachments.flush();
        entityManager.clear();
        assertThat(attachments.totalSizeByBugId(parent.getId())).isEqualTo(20);
        assertThat(bugs.findById(parent.getId()).orElseThrow().getUpdatedAt()).isEqualTo(timestamp);
    }

    @Test
    void foreignKeyRejectsMissingBug() {
        assertThatThrownBy(() -> attachment(Long.MAX_VALUE, 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void queriesReturnOnlyAttachmentsOfRequestedBug() {
        Bug firstBug = bug();
        Bug secondBug = bug();
        BugAttachment firstAttachment = attachment(firstBug.getId(), 10);
        BugAttachment secondAttachment = attachment(firstBug.getId(), 20);
        BugAttachment thirdAttachment = attachment(secondBug.getId(), 100);
        entityManager.clear();
        assertThat(attachments.countByBugId(firstBug.getId())).isEqualTo(2);
        assertThat(attachments.countByBugId(secondBug.getId())).isEqualTo(1);
        assertThat(attachments.totalSizeByBugId(firstBug.getId())).isEqualTo(30);
        assertThat(attachments.totalSizeByBugId(secondBug.getId())).isEqualTo(100);
        assertThat(attachments.findByBugIdOrderByCreatedAtAscIdAsc(firstBug.getId()))
                .extracting(BugAttachment::getId)
                .containsExactly(firstAttachment.getId(), secondAttachment.getId());
        assertThat(attachments.findByBugIdOrderByCreatedAtAscIdAsc(secondBug.getId()))
                .extracting(BugAttachment::getId)
                .containsExactly(thirdAttachment.getId());
        assertThat(attachments.findByIdAndBugId(thirdAttachment.getId(), firstBug.getId())).isEmpty();

    }
}
