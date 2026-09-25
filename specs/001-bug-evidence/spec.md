# Feature: Bug Attachments and Related Task Link

## 1. Status

Updated after customer decisions on attachment timestamps, batch total-size overflow and original download filenames. Remaining business questions are listed in section 9.

This specification contains only requirements agreed during clarification. Unresolved behavior is listed under **Open Questions** and must not be invented during implementation.

---

## 2. Goal

Allow BugPocket users to attach visual evidence to a bug in the form of screenshots and videos, and optionally associate the bug with one external task by URL.

---

## 3. Scope

This feature includes:

- adding image attachments to a bug;
- adding video attachments to a bug;
- selecting attachments while creating a bug;
- adding attachments after the bug already exists;
- displaying a preview for image attachments;
- displaying a preview for video attachments;
- displaying the attachment file type;
- downloading an attachment;
- deleting an attachment;
- storing one optional related-task URL on a bug;
- adding, changing and removing the related-task URL.

---

## 4. Definitions

**Attachment** — an image or video file associated with a bug.

**Image attachment** — an attachment in PNG, JPG or JPEG format.

**Video attachment** — an attachment in MP4, MOV, MKV, AVI or WEBM format.

**Attachment preview** — a small visual representation of the attached image or video shown in the bug UI.

**Upload batch** — the files submitted together in one upload operation. Per-file validation identifies eligible files and rejected files before any attachment from the batch is saved. For the aggregate size check, batch size is the sum of eligible file sizes; files rejected by per-file validation do not consume attachment capacity.

**Related task URL** — an optional HTTP/HTTPS link to one external task related to the bug. The external system is not restricted to Jira or any other specific product.

---

## 5. User Stories

### US-01. Attach screenshot

As a BugPocket user, I want to attach a screenshot to a bug so that I can visually demonstrate the problem.

### US-02. Attach video

As a BugPocket user, I want to attach a video to a bug so that I can demonstrate the sequence of actions and observed behavior.

### US-03. Manage attachments

As a BugPocket user, I want to add, download and delete attachments so that the evidence associated with a bug can be maintained over time.

### US-04. Link related task

As a BugPocket user, I want to add a related-task URL so that I can navigate from the bug to the external task associated with its resolution.

---

## 6. Functional Requirements

### FR-01. Attachment creation timing

The system must allow attachments to be selected while creating a bug and must also allow attachments to be added after the bug has been created.

### FR-02. Attachment count limit

A bug must have no more than 10 attachments at the same time.

The rule for a batch that fits the byte limit but exceeds the remaining attachment count is still open (Q-09). The total-size rule in FR-04 must not silently be extended to count overflow.

### FR-03. Per-file size limit

The size of one attachment must be less than or equal to 25 MB (25,000,000 bytes).

A file whose size is exactly 25 MB is valid, provided the total attachment-size limit is also satisfied.

### FR-04. Total attachment-size limit

The total size of all attachments associated with one bug must be less than or equal to 25 MB (25,000,000 bytes).

Before saving any attachment from a batch, the system must compare the total size of its eligible files with the remaining size capacity of that bug. If accepting the batch would exceed 25 MB in total, the ENTIRE batch must be rejected. No file from that batch may be saved, even if some files would fit individually. Existing attachments must remain unchanged.

The user must receive a batch-level error explaining that the total attachment-size limit would be exceeded. Equality with the limit is allowed, provided the count limit is satisfied. Q-04 is closed: both limits use exactly 25,000,000 bytes.

### FR-05. Supported image formats

The system must accept image attachments in these formats:

- PNG;
- JPG;
- JPEG.

### FR-06. Supported video formats

The system must accept video attachments in these formats:

- MP4;
- MOV;
- MKV;
- AVI;
- WEBM.

### FR-07. Unsupported files

Files outside the supported image and video formats must not be attached to a bug.

For every rejected file, the user must receive an error for that file.

### FR-08. Partial success for multi-file upload

When multiple files are submitted together, each file must be validated independently for file-specific requirements, including supported format and per-file size.

If the eligible files collectively fit the remaining total-size and count capacity, they must be attached even when other files in the same submission are rejected. Each rejected file must have its own error. A file-specific rejection must not cancel the other eligible files.

FR-04 takes precedence when the eligible batch exceeds the remaining total-size capacity: reject the entire batch before saving any of its attachments, rather than choosing files that individually fit. This is a batch-level quota decision, not a replacement for per-file partial success. Count-overflow behavior remains Q-09.

Example: image.png and video.mp4 are valid; document.exe is unsupported. If image.png + video.mp4 fit the remaining capacity, save those two and return a per-file error for document.exe. If their combined size exceeds the remaining byte capacity, save none of the batch and return the total-size error; the unsupported-file error remains attributable to document.exe.

### FR-09. Attachment preview

For an image attachment, the system must display a small image preview and its file type.

For a video attachment, the system must display a small video preview and its file type.

### FR-10. Attachment download

The system must allow a user to download an existing attachment.

The downloaded file must have the original uploaded filename and the original file contents. The system must retain the original filename as attachment metadata (FR-19).

### FR-11. Attachment deletion

The system must allow a user to delete an existing attachment from a bug.

After successful deletion, the attachment must no longer be associated with that bug.

### FR-12. Related-task URL optionality

The related-task URL is optional.

A bug must be creatable and editable without a related-task URL.

### FR-13. Related-task URL cardinality

A bug may contain at most one related-task URL.

### FR-14. Related-task URL lifecycle

A user must be able to:

- set the URL while creating a bug;
- set the URL after the bug has been created;
- replace the existing URL;
- remove the existing URL.

### FR-15. Supported URL schemes

The stored related-task URL must use HTTP or HTTPS.

The URL is not restricted to a specific external task-management system.

### FR-16. URL normalization

If the user enters a URL without `http://` or `https://`, the system must automatically prepend `https://` before storing it.

Example:

`tracker.company.local/TASK-123`

must be normalized to:

`https://tracker.company.local/TASK-123`

### FR-17. Invalid URL

If the provided value cannot be accepted as a valid HTTP/HTTPS URL after normalization, the system must reject the value and show an input error to the user.

### FR-18. Attachment operations do not change Bug.updatedAt

Adding or deleting attachments must not change the parent bug's updatedAt. This applies to successful uploads, partial-success uploads and deletions; rejected uploads must not change it either.

Changing relatedTaskUrl through the existing bug editing flow may change updatedAt according to the existing Bug update lifecycle. Attachment operations must not undo timestamps legitimately changed by independent bug edits.

### FR-19. Original filename metadata and safe storage identity

The original uploaded filename must be stored as attachment metadata and used for download. The original filename must not be used as a physical storage path.

Physical storage must use a safe internal storage key independent of the original filename. Different attachments may have the same original filename without overwriting each other's files.

---

## 7. Acceptance Criteria

### AC-01. Add supported image

**Given** a bug can accept another attachment  
**And** adding the file does not make the total attachment size exceed 25 MB  
**When** the user adds a valid PNG, JPG or JPEG file whose size is no more than 25 MB  
**Then** the file is attached successfully.

### AC-02. Add supported video

**Given** a bug can accept another attachment  
**And** adding the file does not make the total attachment size exceed 25 MB  
**When** the user adds a valid MP4, MOV, MKV, AVI or WEBM file whose size is no more than 25 MB  
**Then** the file is attached successfully.

### AC-03. Exactly 25 MB

**Given** the bug has no other attachments  
**When** the user adds one supported file whose size is exactly 25 MB  
**Then** the file is accepted.

### AC-04. File larger than 25 MB

**When** the user attempts to attach a supported file larger than 25 MB  
**Then** the file is rejected  
**And** the user receives an error for that file.

### AC-05. Total size exactly 25 MB

**Given** the bug already has attachments  
**When** another valid file is added and the resulting total attachment size becomes exactly 25 MB  
**Then** the file is accepted.

### AC-06. Total size above 25 MB

**Given** the bug already has attachments  
**When** another file would make the resulting total attachment size greater than 25 MB  
**Then** that file must not be attached  
**And** the user receives an error for that file.

### AC-07. Maximum attachment count

**Given** a bug already has 10 attachments  
**When** the user attempts to add another attachment  
**Then** the new attachment is rejected  
**And** the existing 10 attachments remain unchanged.

### AC-08. Unsupported file type

**When** the user attempts to attach an unsupported file type  
**Then** that file is rejected  
**And** the user receives an error for that file.

### AC-09. Partial success

**Given** the user submits three files together  
**And** two files satisfy all file-specific requirements\
**And** the two eligible files together fit the remaining total-size and count capacity\
**And** one file violates a file-specific attachment requirement\
**When** the upload is processed  
**Then** the two valid files are attached  
**And** the invalid file is not attached  
**And** the user receives an error for the invalid file.

### AC-10. Image preview

**Given** a bug has a supported image attachment  
**When** the user opens the bug  
**Then** a small preview of the image is displayed  
**And** the file type is displayed.

### AC-11. Video preview

**Given** a bug has a supported video attachment  
**When** the user opens the bug  
**Then** a small preview of the video is displayed  
**And** the file type is displayed.

### AC-12. Download attachment

**Given** a bug has an attachment  
**When** the user chooses to download it  
**Then** the system provides the original file contents for download\
**And** the download filename is the original uploaded filename.

### AC-13. Delete attachment

**Given** a bug has an attachment  
**When** the user deletes that attachment  
**Then** the attachment is no longer associated with the bug  
**And** it is no longer shown among the bug attachments.

### AC-14. Create bug without related-task URL

**When** the user creates a valid bug without a related-task URL  
**Then** the bug is created successfully.

### AC-15. Store HTTP/HTTPS URL

**When** the user provides a valid `http://` or `https://` related-task URL  
**Then** the URL is stored for the bug.

### AC-16. Automatically add HTTPS

**When** the user provides a related-task URL without `http://` or `https://`  
**And** the value can be accepted as a valid URL after normalization  
**Then** the system prepends `https://`  
**And** stores the normalized URL.

### AC-17. Replace related-task URL

**Given** the bug already has a related-task URL  
**When** the user replaces it with another valid URL  
**Then** the new URL is stored instead of the previous URL.

### AC-18. Remove related-task URL

**Given** the bug has a related-task URL  
**When** the user removes the value and saves the bug  
**Then** the bug is stored without a related-task URL.

### AC-19. Invalid related-task URL

**When** the user provides a value that remains invalid after URL normalization  
**Then** the value is rejected  
**And** the user receives an input error.

### AC-20. Reject the entire batch on total-size overflow

**Given** a bug has existing attachments
**And** each of two new eligible files would individually fit the remaining byte capacity
**But** their combined size exceeds that remaining capacity
**When** the files are uploaded in one batch
**Then** neither file is saved as an attachment
**And** existing attachments remain unchanged
**And** the user receives a batch-level total-size-limit error.

The same zero-save result applies to an overflowing eligible batch containing additional files rejected by per-file validation. File-specific errors remain associated with their files.

### AC-21. Batch reaches the total-size limit exactly

**Given** a bug can accept all eligible files within the 10-attachment limit
**When** existing attachment bytes plus the eligible batch bytes equal exactly 25 MB
**Then** all eligible files are attached
**And** any independently invalid files are rejected with their own errors.

### AC-22. Attachment operations preserve updatedAt

**Given** a bug has a recorded updatedAt and no concurrent edit to the bug itself
**When** attachments are added or deleted, including a partial-success upload
**Then** the bug's updatedAt is unchanged.

A completely rejected batch must also leave updatedAt unchanged.

### AC-23. Related-task URL uses the existing update lifecycle

**Given** a bug has an existing related-task URL
**When** the user changes the URL through ordinary bug editing
**Then** the URL is updated
**And** updatedAt follows the existing Bug update logic, without attachment-specific timestamp suppression.

### AC-24. Original names are independent of storage paths

**Given** two accepted attachments have the same original uploaded filename
**When** they are stored and downloaded
**Then** each retains its own original bytes and metadata filename
**And** storage uses independent safe internal keys without overwriting either file
**And** no user-provided filename determines a physical storage path.

---

## 8. Out of Scope

The following are not part of this feature unless added by a later specification:

- more than one related-task URL per bug;
- automatic integration with Jira, YouTrack, GitHub, GitLab or another external task system;
- checking whether the external task actually exists;
- fetching external task title, status or metadata;
- editing the contents of an attachment inside BugPocket;
- storing unsupported document/archive/executable formats as attachments;
- user permissions or role-based access control for attachments;
- cloud/object storage such as S3 or MinIO as a business requirement.

---

## 9. Decisions and remaining business questions

### Closed customer decisions

- **Q-04 — CLOSED (2026-09-25):** 25 MB means exactly 25,000,000 bytes for both per-file and aggregate limits. Equality is allowed; exceeding the limit by even one byte is rejected. This decision no longer blocks implementation or acceptance tests.

- **OQ-01 — CLOSED:** adding/deleting attachments does not change Bug.updatedAt (FR-18, AC-22). Related-task URL edits retain the existing update lifecycle (AC-23).
- **OQ-02 — total-size decision CLOSED:** reject the entire eligible batch before saving any attachment when it exceeds remaining total-size capacity (FR-04/08, AC-20/21). No winner selection by size/order is needed. The former question also mentioned count overflow; that distinct unresolved case is Q-09 below.
- **OQ-03 — CLOSED:** preserve the original filename for download, store it in metadata, and use an independent safe storage key (FR-10/19, AC-12/24).

### Remaining business questions

These questions do not reopen the decisions above and block only the affected behavior.

- **Q-09 — Batch count overflow:** if the batch fits the remaining byte capacity but exceeds the remaining slots out of 10 attachments, should the whole batch be rejected or a subset accepted? If a subset, which files? For example, a bug has 9 attachments and two valid small files are submitted. The customer decision about total size does not answer this case.
- **Q-06 — Preview failure for otherwise valid video:** if a supported valid video cannot obtain its required preview because generation fails, should the attachment be rejected with an individual error or retained with an explicit preview-error/retry state? This is user-visible lifecycle behavior, not a request to choose a decoding tool. A generic icon does not meet FR-09. The implementation must first try to satisfy all required formats; it must not silently narrow the supported formats/codecs to evade this question.

### Technical matters, not customer questions

URI parser, storage-key generation, database locking, DTO names, media detection library, preview runtime and multipart configuration are implementation choices. They must satisfy the specified behavior and be tested.

Earlier analysis questions Q-05 (URL parser/input matrix), Q-07 (multipart mechanics) and Q-08 (omitted URL in PUT) are not retained as customer blockers. Follow HTTP/HTTPS requirements, preserve the existing full-update API semantics, and handle transport errors without weakening the specified batch/partial-success rules. No new business URL length limit, external-task lookup or format restriction is authorized by this clarification.
