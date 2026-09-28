package ru.codex.codextest.service;
import java.util.List;
import ru.codex.codextest.dto.AttachmentResponse;
public record AttachmentBatchResponse(List<AttachmentResponse> accepted, List<AttachmentUploadError> errors) { }
