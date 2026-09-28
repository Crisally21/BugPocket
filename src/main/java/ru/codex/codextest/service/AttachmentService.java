package ru.codex.codextest.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.codex.codextest.exception.AttachmentNotFoundException;
import ru.codex.codextest.exception.BugNotFoundException;
import ru.codex.codextest.dto.AttachmentResponse;
import ru.codex.codextest.model.AttachmentMediaKind;
import ru.codex.codextest.model.AttachmentFileType;
import ru.codex.codextest.model.BugAttachment;
import ru.codex.codextest.repository.AttachmentRepository;
import ru.codex.codextest.repository.BugRepository;

import java.util.List;
import java.io.IOException;
import java.util.ArrayList;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class AttachmentService {
    private final BugRepository bugRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentValidator validator;
    private final AttachmentStorage storage;
    private final VideoPreviewGenerator previewGenerator;

    @Autowired
    public AttachmentService(BugRepository bugRepository, AttachmentRepository attachmentRepository,
                             AttachmentValidator validator, AttachmentStorage storage,
                             VideoPreviewGenerator previewGenerator) {
        this.bugRepository = bugRepository;
        this.attachmentRepository = attachmentRepository;
        this.validator = validator;
        this.storage = storage;
        this.previewGenerator = previewGenerator;
    }

    public AttachmentService(BugRepository bugRepository, AttachmentRepository attachmentRepository) {
        this(bugRepository, attachmentRepository, new AttachmentValidator(), null, null);
    }

    public List<AttachmentResponse> findByBugId(long bugId) {
        if (!bugRepository.existsById(bugId)) {
            throw new BugNotFoundException(bugId);
        }
        return attachmentRepository.findByBugIdOrderByCreatedAtAscIdAsc(bugId).stream()
                .map(AttachmentResponse::from).toList();
    }

    public BugAttachment findById(long bugId, long attachmentId) {
        if (!bugRepository.existsById(bugId)) {
            throw new BugNotFoundException(bugId);
        }
        return attachmentRepository.findByIdAndBugId(attachmentId, bugId)
                                   .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
    }

    @Transactional
    public AttachmentBatchResponse upload(long bugId, List<MultipartFile> files) {
        requireBug(bugId);
        List<AttachmentResponse> accepted = new ArrayList<>();
        List<AttachmentUploadError> errors = new ArrayList<>();
        long remaining = 25_000_000L - attachmentRepository.totalSizeByBugId(bugId);
        List<MultipartFile> valid = new ArrayList<>();
        long total = 0;
        for (MultipartFile file : files == null ? List.<MultipartFile>of() : files) {
            try {
                validator.validateNotEmpty(file);
                validator.validateSize(file);
                AttachmentFileType type = AttachmentFileType.fromFilename(file.getOriginalFilename());
                if (type.getMediaKind() == AttachmentMediaKind.IMAGE) validator.validateImageContent(file);
                if (type == AttachmentFileType.PDF) validator.validatePdfContent(file);
                total += file.getSize();
                valid.add(file);
            } catch (RuntimeException error) {
                errors.add(new AttachmentUploadError(file == null ? null : file.getOriginalFilename(), error.getMessage()));
            }
        }
        if (total > remaining) {
            for (MultipartFile file : valid) errors.add(new AttachmentUploadError(file.getOriginalFilename(), "Недостаточно места в квоте вложений"));
            return new AttachmentBatchResponse(List.of(), errors);
        }
        for (MultipartFile file : valid) {
            String originalKey = null;
            String previewKey = null;
            try {
                AttachmentFileType type = AttachmentFileType.fromFilename(file.getOriginalFilename());
                originalKey = storage.store(file.getInputStream());
                if (type.getMediaKind() == AttachmentMediaKind.VIDEO) {
                    byte[] preview = previewGenerator.generatePreview(file.getInputStream());
                    previewKey = storage.store(new java.io.ByteArrayInputStream(preview));
                }
                BugAttachment entity = new BugAttachment();
                entity.setBugId(bugId);
                entity.setMediaKind(type.getMediaKind());
                entity.setFileType(type);
                entity.setContentType(type.getContentType());
                entity.setSizeBytes(file.getSize());
                entity.setStorageKey(originalKey);
                entity.setPreviewKey(previewKey);
                entity.setOriginalFilename(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
                accepted.add(AttachmentResponse.from(attachmentRepository.saveAndFlush(entity)));
            } catch (Exception error) {
                if (previewKey != null) try { storage.delete(previewKey); } catch (IOException ignored) { }
                if (originalKey != null) try { storage.delete(originalKey); } catch (IOException ignored) { }
                errors.add(new AttachmentUploadError(file.getOriginalFilename(), error.getMessage()));
            }
        }
        return new AttachmentBatchResponse(accepted, errors);
    }

    @Transactional
    public void delete(long bugId, long attachmentId) throws IOException {
        requireBug(bugId);
        BugAttachment attachment = attachmentRepository.findByIdAndBugId(attachmentId, bugId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        attachmentRepository.delete(attachment);
        storage.delete(attachment.getStorageKey());
        if (attachment.getPreviewKey() != null) storage.delete(attachment.getPreviewKey());
    }

    private void requireBug(long bugId) {
        if (!bugRepository.existsById(bugId)) throw new BugNotFoundException(bugId);
    }
}
