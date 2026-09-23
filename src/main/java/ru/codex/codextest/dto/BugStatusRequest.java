package ru.codex.codextest.dto;

import jakarta.validation.constraints.NotNull;
import ru.codex.codextest.model.BugStatus;

public record BugStatusRequest(@NotNull(message = "Укажи статус") BugStatus status) {
}
