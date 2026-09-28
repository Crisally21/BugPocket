package ru.codex.codextest.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.codex.codextest.exception.AttachmentNotFoundException;
import ru.codex.codextest.exception.BugNotFoundException;
import ru.codex.codextest.model.BugAttachment;
import ru.codex.codextest.repository.AttachmentRepository;
import ru.codex.codextest.repository.BugRepository;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AttachmentService {
    private final BugRepository bugRepository;
    private final AttachmentRepository attachmentRepository;

    public AttachmentService(BugRepository bugRepository, AttachmentRepository attachmentRepository) {
        this.bugRepository = bugRepository;
        this.attachmentRepository = attachmentRepository;
    }

    public List<BugAttachment> findByBugId(long bugId) {
        if (!bugRepository.existsById(bugId)) {
            throw new BugNotFoundException(bugId);
        }
        return attachmentRepository.findByBugIdOrderByCreatedAtAscIdAsc(bugId);
    }

    public BugAttachment findById(long bugId, long attachmentId) {
        if (!bugRepository.existsById(bugId)) {
            throw new BugNotFoundException(bugId);
        }
        return attachmentRepository.findByIdAndBugId(attachmentId, bugId)
                                   .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
    }
}
