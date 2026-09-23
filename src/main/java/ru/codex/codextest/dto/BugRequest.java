package ru.codex.codextest.dto;

import jakarta.validation.constraints.*;
import ru.codex.codextest.model.BugPriority;

public record BugRequest(
        @NotBlank(message = "Укажи название бага") @Size(max = 200) String header,
        @Size(max = 10000) String steps,
        @Size(max = 10000) String actualResult,
        @Size(max = 10000) String expectedResult,
        @Size(max = 2000) String environment,
        @NotNull(message = "Укажи приоритет") BugPriority priority) {
}
