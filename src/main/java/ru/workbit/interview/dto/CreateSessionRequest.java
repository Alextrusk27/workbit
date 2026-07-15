package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.Profession;

public record CreateSessionRequest(
        @Schema(description = "Профессия, по которой проводится собеседование", example = "Java-разработчик")
        @NotNull
        Profession profession,

        @Schema(description = "Целевой уровень кандидата", example = "Middle")
        @NotNull
        Level level
) {
}
