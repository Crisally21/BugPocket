package ru.codex.codextest.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.codex.codextest.model.*;

public interface BugRepository extends JpaRepository<Bug, Long> {
    @Query("""
            select b from Bug b
            where (:status is null or b.status = :status)
              and (:priority is null or b.priority = :priority)
            order by b.createdAt desc, b.id desc
            """)
    List<Bug> findFiltered(@Param("status") BugStatus status, @Param("priority") BugPriority priority);
}
