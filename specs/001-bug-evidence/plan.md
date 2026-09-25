# Implementation Plan: Bug Attachments and Related Task Link

## 1. Purpose

This document describes the proposed technical implementation for `spec.md` in the current BugPocket codebase.

The plan may define technical choices, but it must not resolve business behavior listed as an Open Question in `spec.md`.

---

## 2. Existing Architecture

The current BugPocket application uses:

- Java 17;
- Spring Boot MVC;
- Spring Data JPA;
- PostgreSQL;
- Liquibase;
- Bean Validation;
- static HTML/CSS/JavaScript served by Spring, with client-side rendering;
- JSON-based create/update operations for bugs;
- MockMvc/H2 backend tests;
- JavaScript frontend tests.

The existing JSON API for creating and editing bugs should remain compatible.

---

## 3. Related Task URL

### 3.1 Data model

Add a nullable `relatedTaskUrl` field to `Bug`.

Add a nullable `related_task_url` column to the `bug` table through a new Liquibase changeSet.

Do not modify the already existing initial `1-create-bug` changeSet.

### 3.2 DTOs

Add `relatedTaskUrl` to:

- `BugRequest`;
- `BugResponse`.

Requests that omit the field must continue to work. Preserve the current full-replacement semantics of PUT for editable fields: an omitted nullable relatedTaskUrl is treated as absent, just like existing nullable text fields; null/empty/blank clears it. Do not introduce PATCH-like retention semantics into PUT.

### 3.3 Normalization and validation

Normalize and validate the related-task URL on the backend.

Required behavior:

- blank/empty input becomes no related-task URL;
- an input already starting with `http://` remains HTTP;
- an input already starting with `https://` remains HTTPS;
- an input without either scheme receives `https://`;
- the resulting value must be accepted as a valid HTTP/HTTPS URL;
- invalid values result in a normal API validation error.

The frontend may perform equivalent validation for UX, but backend validation is authoritative. Parser selection and standards-based handling of URL syntax are technical choices. Explicit non-HTTP(S) schemes are invalid; do not disguise them by blindly prepending HTTPS. Do not contact the external task system or introduce an arbitrary business URL-length limit.

Related-task URL edits go through BugService.apply and the existing Bug update lifecycle, including @PreUpdate. Attachment timestamp rules must not disable timestamps for ordinary bug edits.

---

## 4. Attachment Domain Model

Create a new entity, for example `BugAttachment`.

The entity should store attachment metadata, not the file bytes themselves.

Suggested fields:

- `id`;
- relation/reference to `Bug`;
- `mediaKind` (`IMAGE` or `VIDEO`);
- `fileType` (`PNG`, `JPG`, `JPEG`, `MP4`, `MOV`, `MKV`, `AVI`, `WEBM`);
- `contentType`;
- `sizeBytes`;
- `storageKey`;
- `originalFilename` (required metadata for download);
- `createdAt`.

A reference to a derived video preview may be added as an implementation detail.

OQ-03 is resolved: preserve the original uploaded filename in metadata and in the download response. Never use it as a filesystem path. Generate independent storage keys, including for equal original names. Encode Content-Disposition safely (including Unicode) rather than concatenating raw header values; do not expose storage keys or absolute paths in public metadata.

Attachment add/delete must not dirty or explicitly save the parent Bug just to update its timestamp. Avoid mapping/cascade or counter updates that trigger Bug.@PreUpdate. Do not restore a stale timestamp over a concurrent real bug edit.

Create an `AttachmentRepository` using Spring Data JPA.

Repository operations will need to support at least:

- listing attachments for a bug;
- counting attachments for a bug;
- calculating or retrieving total attachment size for a bug;
- locating one attachment while verifying it belongs to the requested bug.

---

## 5. File Storage

### 5.1 Storage approach

For the current local BugPocket application, use a filesystem-based storage abstraction rather than storing large binary files in PostgreSQL.

Introduce a storage component such as `AttachmentStorage` with operations conceptually equivalent to:

- store file;
- open/read file;
- delete file.

### 5.2 Storage key

Physical files must be stored under generated internal storage keys rather than directly using a user-provided filename as a filesystem path.

The configured storage root must be the only directory accessible through this component; path traversal outside the storage root must be prevented.

### 5.3 Configuration

Add a configurable attachment storage root in application configuration.

Tests must use a temporary directory instead of the developer's real attachment directory.

---

## 6. Attachment Validation

Create attachment-validation logic in the backend service layer.

Authoritative business checks:

- at most 10 attachments per bug;
- each file size <= 25 MB;
- total attachment size per bug <= 25 MB;
- allowed image formats: PNG, JPG, JPEG;
- allowed video formats: MP4, MOV, MKV, AVI, WEBM;
- reject the entire eligible batch when its combined size exceeds remaining total-size capacity, before saving any attachment from that batch (spec FR-04/08).

Spec Q-04 is closed: use exactly 25,000,000 bytes for both per-file and aggregate limits, with equality allowed. A rejected file does not consume quota. The unresolved count-overflow policy is Q-09; do not assume it follows the size-overflow policy.

Do not trust only a browser-provided extension or `Content-Type` header when deciding whether a file is acceptable. The implementation should validate the actual file format using an appropriate server-side strategy.

The exact library/strategy may be chosen during implementation, but it must support the formats required by `spec.md`.

---

## 7. Attachment API

Keep attachment operations separate from the existing JSON bug create/update API.

Proposed endpoints:

- `GET /api/bugs/{bugId}/attachments`
  - list attachment metadata for a bug;

- `POST /api/bugs/{bugId}/attachments`
  - accept `multipart/form-data` containing one or more files;
  - validate each file and collect eligible files plus individual errors;
  - check the whole eligible batch against the remaining total-size capacity before persistence;
  - reject the whole batch on aggregate overflow with a batch-level error and zero saved attachments;
  - otherwise persist eligible files under the count policy and return accepted items plus per-file errors;

- `GET /api/bugs/{bugId}/attachments/{attachmentId}/content`
  - return the original image for an image attachment, or a generated still image for a video attachment; use the MIME of the returned preview, not the original video's MIME;

- `GET /api/bugs/{bugId}/attachments/{attachmentId}/download`
  - return original attachment bytes with the original uploaded filename in Content-Disposition;

- `DELETE /api/bugs/{bugId}/attachments/{attachmentId}`
  - remove the attachment and its stored file.

The exact response DTO names are implementation details, but responses must allow the frontend to distinguish accepted files from rejected files in a partial-success upload.

---

## 8. Create-Bug Flow With Attachments

Do not convert the existing `POST /api/bugs` JSON contract to multipart solely because this feature adds attachments.

Frontend flow:

1. validate the bug form;
2. create the bug using the existing JSON API;
3. obtain the new bug ID;
4. if files were selected, upload them through the attachment API;
5. show any per-file errors or whole-batch size error without deleting the already created bug or any successfully accepted attachments;
6. navigate to the created bug/detail view.

This preserves backward compatibility with the current API. Retain upload results across navigation so errors remain visible in the detail view. After a successful JSON POST, an upload retry must use that bug ID instead of creating another bug. Selected File objects must stay out of the JSON bug DTO.

---

## 9. Existing-Bug Flow

On an existing bug page, the frontend must be able to:

- load attachment metadata;
- display previews;
- add more files while limits allow;
- download an attachment;
- delete an attachment.

The related-task URL should be editable through the existing bug edit flow.

---

## 10. Preview Strategy

### 10.1 Images

For image attachments, the frontend can display a scaled preview using attachment content returned by the backend.

A separate server-generated thumbnail is not required for the initial image implementation unless performance testing demonstrates a need.

### 10.2 Videos

The specification requires a preview for all supported video formats, including formats that browsers do not uniformly preview/play natively.

Therefore the implementation should not rely exclusively on `<video>` native playback for preview generation.

Introduce a video-preview abstraction, for example `VideoPreviewGenerator`.

Preferred technical direction:

- generate a still preview frame server-side;
- store or cache the generated preview separately from the original video;
- serve the generated image to the frontend for the attachment card.

FFmpeg is a likely implementation tool because it supports the required containers/codecs broadly, but the implementation task must first verify how the runtime dependency will be supplied in the local development environment.

If no supported video-decoding solution is available in the runtime, treat video-preview implementation as a technical blocker rather than silently replacing the required preview with a generic icon. Choosing and supplying the tool is an engineering task within T-05, not a customer business question. Check real fixtures for all five containers and do not silently narrow format support.

For an otherwise valid video whose preview generation fails, the user-visible retain/reject lifecycle is still spec Q-06. Keep this separate from choosing a library. Apply the confirmed policy to per-file results; clean up abandoned originals and derived previews.

---

## 11. Batch admission and partial success

Use two distinct validation levels:

1. Identify per-file errors (unsupported format, per-file size, etc.) and eligible files. Associate results with an input index or client correlation ID so identical filenames remain distinguishable.
2. Before persisting any attachment, compare existing bytes + the sum of all eligible file sizes with the per-bug limit. Rejected files do not enter this sum, matching the customer's image.png + video.mp4 + document.exe example.
3. If the sum exceeds the limit, reject the ENTIRE batch. Return a total-size-limit error, no uploaded items, and applicable individual errors. Do not accept a subset, sort files to fit, or save until the limit is reached.
4. If aggregate bytes fit (including equality), apply the count policy once Q-09 is resolved and persist eligible files. A file-specific rejection must not cancel other eligible files.

The size decision must use a concurrency-safe view of quota before writes. If another upload has consumed capacity, re-evaluate the entire batch before persistence, not after some files have been committed.

A successful partial result can contain uploaded items and an errors array. A whole-batch quota rejection can use ProblemDetail with a stable code such as ATTACHMENT_TOTAL_SIZE_EXCEEDED and an optional per-file errors extension. Exact status/DTO names are implementation details; the frontend must unambiguously distinguish batch rejection from partial success and form-field validation.

This is whole-batch rejection for aggregate-size overflow, not a new requirement that every runtime I/O failure roll back the whole batch. Handle operational failures and compensation explicitly, preserving the specified per-file behavior. Do not silently resolve Q-06/Q-09.

---

## 12. Database Migrations

Add new Liquibase changeSets rather than editing the initial schema changeSet.

Expected changes:

1. add nullable `related_task_url` to `bug`;
2. create `bug_attachment` with a foreign key to `bug` and original_filename metadata distinct from storage_key;
3. add indexes only where query patterns justify them.

The exact migration IDs are technical details, but they must be new and stable.

---

## 13. Transaction and File Consistency

A PostgreSQL transaction cannot roll back filesystem changes.

The attachment service must therefore use compensation/error cleanup where necessary.

Example failure case:

1. physical file is written successfully;
2. database metadata insert fails;
3. service removes the newly written physical file before propagating the error.

Deletion must also account for database/filesystem consistency.

Do not assume `@Transactional` alone makes file operations atomic.

Perform batch-size admission before durable attachment writes. Temporary multipart/staging files needed to inspect content are not accepted attachments and must be cleaned up on rejection. Do not implement overflow by saving some files and then undoing them.

Choose a concurrency mechanism covering quota admission and persistence for the whole eligible batch: for example, serialize writers by bug with a database lock, or use an equivalent reservation strategy. Separate per-file commits must not let another batch invalidate an already partially persisted admission. The chosen strategy must preserve partial success without a shared rollback-only transaction losing earlier successes. Test on PostgreSQL, not only H2.

Include commit-time failures, original/preview cleanup and failed deletion in the compensation design. Keep expensive decoding outside long database lock holds where possible. Locks/reservations must not alter Bug.updatedAt; avoid parent-row counter updates that invoke its callback. Implementation details belong to T-06, not separate customer questions.

---

## 14. Multipart Configuration

Configure Spring multipart limits so that requests satisfying the business limit can reach application validation.

The HTTP multipart configuration must account for multipart overhead and multi-file requests.

The 25-MB per-file and 25-MB total-per-bug business limits must still be checked in application logic and must not depend solely on Spring's request-size configuration.

A hard 25-MB request cap can discard an otherwise valid mixed batch before per-file handling. Select and test multipart/streaming handling against FR-04/08, including invalid files alongside valid ones and actual HTTP overhead. An infrastructure 413 is not proof of the required business batch check. Malformed or interrupted transport is a request-level error, not a successful per-file result. Do not introduce a new customer-defined batch-size/count limit as a shortcut; report a demonstrated technical limitation before changing business guarantees.

---

## 15. Error Handling

Extend the existing `ApiExceptionHandler`/ProblemDetail approach rather than introducing an unrelated error format.

Expected error categories include:

- bug not found;
- attachment not found or not belonging to the specified bug;
- unsupported file type;
- file too large;
- whole-batch total size limit exceeded (zero saved attachments);
- attachment count limit exceeded;
- storage failure;
- invalid related-task URL.

For a batch admitted by the total-size check, per-file business errors must be representable without turning successfully accepted files into failures. Whole-batch overflow is a separate outcome and must not be shown as partial success.

---

## 16. Frontend Changes

Update the existing static frontend rather than introducing a new frontend framework.

Expected areas:

- add related-task URL input to create/edit form;
- add attachment file picker to create form;
- add attachment section to bug detail view;
- add attachment upload on existing bug;
- show image preview + file type;
- show generated video preview + file type;
- add download action;
- add delete action;
- display per-file validation/upload errors and a distinct whole-batch total-size error;
- preserve original download filenames via the backend download response;
- keep the displayed Bug.updatedAt unchanged for attachment-only operations;
- preserve current dirty-form/saving protections.

The frontend may validate file count/size/type early for UX, but backend validation remains authoritative. Do not split one selected batch into independent requests to bypass whole-batch rejection. Adapt api() to FormData and empty DELETE responses; keep selected files and upload results in explicit state. Preserve stale-response guards, dirty/saving protections and attachment rendering after status changes.

---

## 17. Testing Strategy

### 17.1 Related-task URL tests

Test at minimum:

- omitted URL;
- explicit HTTPS URL;
- explicit HTTP URL;
- URL without scheme receives HTTPS;
- replacing URL;
- clearing URL;
- invalid URL is rejected;
- existing create/update behavior remains compatible.

### 17.2 Attachment API tests

Test at minimum:

- successful PNG/JPG/JPEG upload;
- successful MP4/MOV/MKV/AVI/WEBM upload using representative valid fixture files;
- unsupported type rejected;
- exactly 25-MB valid boundary;
- >25-MB file rejected;
- total exactly 25 MB accepted;
- total >25 MB: entire eligible batch rejected with no persisted originals/metadata/previews, even when individual files would fit;
- eligible batch reaching the total limit exactly accepted;
- invalid document.exe does not consume quota while eligible image.png + video.mp4 fit;
- 10 attachments allowed;
- 11th rejected;
- partial-success batch;
- list attachments;
- preview content;
- download original bytes with original filename, including Unicode and equal names on different attachments;
- deletion;
- missing bug -> 404;
- attachment not belonging to specified bug is not exposed;
- storage cleanup after metadata persistence/commit failure and failed deletion;
- Bug.updatedAt unchanged after upload, partial success, rejected batch and deletion;
- relatedTaskUrl edits retain the existing timestamp behavior;
- concurrent uploads cannot exceed byte/count quota or admit only part of an overflowing batch;
- migrations work on both a fresh database and an existing database without changing 1-create-bug.

### 17.3 Frontend tests

Test at minimum:

- URL input presence and validation behavior;
- attachment selection;
- image preview rendering;
- video preview rendering;
- partial upload result rendering and whole-batch rejection rendering;
- create JSON bug followed by upload, without losing errors or creating duplicate bugs on retry;
- download action wiring;
- delete action wiring;
- existing bug create/edit/status behavior remains functional.

### 17.4 Test storage

Use a temporary filesystem directory for tests.

Tests must not write into the developer's normal attachment storage directory. Clean attachment FK rows before bug rows in test setup. Use representative small media fixtures; generate valid boundary-size fixtures in temporary directories. Verify multipart limits with a real HTTP server, concurrency/upgrade on isolated PostgreSQL, and actual previews/downloads in a browser; jsdom alone does not prove rendering.

---

## 18. Compatibility Requirements

The following existing behavior must remain working:

- `GET /api/bugs`;
- `GET /api/bugs/{id}`;
- `POST /api/bugs` with existing fields only;
- `PUT /api/bugs/{id}` with existing fields only;
- `PATCH /api/bugs/{id}/status`;
- current filters;
- current status and priority rules;
- current validation/error conventions unless explicitly extended.

The feature must extend BugPocket rather than require existing API clients to send multipart data for ordinary bug creation/update.

---

## 19. Decisions, remaining questions and execution boundary

Closed: OQ-01 (attachment mutations preserve updatedAt), OQ-02's total-size rule (whole-batch rejection before persistence), OQ-03 (original filename metadata/download and independent storage key). These are implementation requirements, not blockers.

Q-04 is closed (2026-09-25): 25 MB is exactly 25,000,000 bytes for both limits; equality is allowed. AttachmentValidator implements the per-file size check. Aggregate admission, content validation and video preview remain separate implementation work.

Remaining business questions are maintained only in spec §9:
- Q-09: count overflow when bytes fit;
- Q-06: reject or retain an otherwise valid video if preview generation fails.

They block only dependent behavior. Parser, locking, storage key, response shape and multipart implementation are engineering decisions within the relevant tasks. Earlier broad Q-05/Q-07/Q-08 are not customer blockers; preserve the existing full-update API semantics and specified business guarantees.

The implementation backlog is tasks.md (12 tasks). This documentation update does not start those tasks and does not authorize production code, tests, migrations, frontend or configuration changes.
