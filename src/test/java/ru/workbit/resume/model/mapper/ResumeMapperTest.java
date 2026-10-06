package ru.workbit.resume.model.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.workbit.resume.dto.ResumeResponse;
import ru.workbit.resume.model.Resume;

@DisplayName("ResumeMapperTest")
class ResumeMapperTest {

    private final ResumeMapper mapper = new ResumeMapperImpl();

    @Nested
    @DisplayName("ToResponse")
    class ToResponse {

        @Test
        @DisplayName("Переносит все поля резюме, uploadedAt берётся из createdAt")
        void mapsAllFieldsAndUploadedAtFromCreatedAt() {
            // given
            var resume = Resume.builder()
                    .userId(UUID.randomUUID())
                    .name("Иванов Java")
                    .originalFilename("Иванов Java.docx")
                    .format(Resume.Format.DOCX)
                    .sizeBytes(184320)
                    .build();

            // when
            ResumeResponse dto = mapper.toResponse(resume);

            // then
            assertThat(dto.id()).isEqualTo(resume.getId());
            assertThat(dto.name()).isEqualTo("Иванов Java");
            assertThat(dto.format()).isEqualTo(Resume.Format.DOCX);
            assertThat(dto.originalFilename()).isEqualTo("Иванов Java.docx");
            assertThat(dto.sizeBytes()).isEqualTo(184320);
            assertThat(dto.uploadedAt()).isEqualTo(resume.getCreatedAt());
        }
    }
}
