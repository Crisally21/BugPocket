package ru.codex.codextest.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.codex.codextest.model.BugAttachment;
import ru.codex.codextest.service.*;

@RestController
@RequestMapping("/api/bugs/{bugId}/attachments")
public class AttachmentController {
    private final AttachmentService service;
    private final AttachmentStorage storage;

    public AttachmentController(AttachmentService service, AttachmentStorage storage) {
        this.service = service;
        this.storage = storage;
    }

    @GetMapping
    public List<?> list(@PathVariable long bugId) { return service.findByBugId(bugId); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentBatchResponse upload(@PathVariable long bugId,
                                          @RequestPart("files") List<MultipartFile> files) {
        return service.upload(bugId, files);
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable long bugId, @PathVariable long attachmentId)
            throws IOException {
        BugAttachment attachment = service.findById(bugId, attachmentId);
        InputStream stream = storage.open(attachment.getStorageKey());
        String filename = attachment.getOriginalFilename().replaceAll("[\\r\\n\\\"]", "_");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable long bugId, @PathVariable long attachmentId)
            throws IOException {
        BugAttachment attachment = service.findById(bugId, attachmentId);
        String key = attachment.getPreviewKey() == null ? attachment.getStorageKey() : attachment.getPreviewKey();
        InputStream stream = storage.open(key);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                attachment.getPreviewKey() == null ? attachment.getContentType() : "image/png"))
                .body(new InputStreamResource(stream));
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable long bugId, @PathVariable long attachmentId) throws IOException {
        service.delete(bugId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
