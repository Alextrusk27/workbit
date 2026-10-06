package ru.workbit.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import ru.workbit.resume.model.Resume;

public record ResumeResponse(
        @Schema(description = "Идентификатор резюме")
        UUID id,

        @Schema(description = "Название резюме", example = "Иванов Java")
        String name,

        @Schema(description = "Формат исходного файла", example = "PDF")
        Resume.Format format,

        @Schema(description = "Имя исходного файла при загрузке", example = "Иванов Java.pdf")
        String originalFilename,

        @Schema(description = "Размер исходного файла в байтах", example = "184320")
        int sizeBytes,

        @Schema(description = "Момент загрузки")
        Instant uploadedAt
) {
}
