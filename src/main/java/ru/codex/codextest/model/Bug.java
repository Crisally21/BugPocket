package ru.codex.codextest.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "bug")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Bug {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(nullable = false, length = 200)
    private String header;
    @Column(length = 10000)
    private String steps;
    @Column(length = 10000)
    private String actualResult;
    @Column(length = 10000)
    private String expectedResult;
    @Column(length = 2000)
    private String environment;
    @Column(columnDefinition = "text")
    private String relatedTaskUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BugStatus status = BugStatus.NEW;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BugPriority priority = BugPriority.MEDIUM;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
