package ru.codex.codextest.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bug_attachment")
@Getter
@Setter
@NoArgsConstructor
public class BugAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(nullable = false)
    private long bugId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttachmentMediaKind mediaKind;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttachmentFileType fileType;
    @Column(nullable = false, length = 100)
    private String contentType;
    @Column(nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 36, unique = true)
    private String storageKey;
    @Column(length = 36)
    private String previewKey;
    @Column(nullable = false, columnDefinition = "text")
    private String originalFilename;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
