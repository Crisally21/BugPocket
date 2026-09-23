package ru.codex.codextest.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.codex.codextest.model.BugAttachment;

public interface AttachmentRepository extends JpaRepository<BugAttachment, Long> {
    List<BugAttachment> findByBugIdOrderByCreatedAtAscIdAsc(long bugId);
    long countByBugId(long bugId);
    Optional<BugAttachment> findByIdAndBugId(long id, long bugId);

    @Query("select coalesce(sum(a.sizeBytes), 0) from BugAttachment a where a.bugId = :bugId")
    long totalSizeByBugId(@Param("bugId") long bugId);
}
