package ru.workbit.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record RenameResumeRequest(
        @Schema(description = "Новое название резюме", example = "Иванов Java")
        @NotBlank
        @Size(max = 100)
        @Nullable String name
) {
    public RenameResumeRequest {
        name = name == null ? null : name.strip();
    }
}
