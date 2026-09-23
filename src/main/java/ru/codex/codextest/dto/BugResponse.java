package ru.codex.codextest.dto;

import java.time.Instant;
import ru.codex.codextest.model.*;

public record BugResponse(long id, String header, String steps, String actualResult,
                          String expectedResult, String environment, BugStatus status,
                          BugPriority priority, Instant createdAt, Instant updatedAt, String relatedTaskUrl) {
    public static BugResponse from(Bug bug) {
        return new BugResponse(bug.getId(), bug.getHeader(), bug.getSteps(), bug.getActualResult(),
                bug.getExpectedResult(), bug.getEnvironment(), bug.getStatus(), bug.getPriority(),
                bug.getCreatedAt(), bug.getUpdatedAt(), bug.getRelatedTaskUrl());
    }
}
