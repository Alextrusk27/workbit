package ru.workbit.training.model.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.workbit.training.dto.CreateSessionRequest;
import ru.workbit.training.dto.TrainingSessionResponse;
import ru.workbit.training.model.TrainingSession;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("TrainingSessionMapperTest")
class TrainingSessionMapperTest {

    private final TrainingSessionMapper mapper = new TrainingSessionMapperImpl();

    @Nested
    @DisplayName("ToEntity")
    class ToEntity {

        @Test
        @DisplayName("Переносит skill/profession/level и не трогает игнорируемые поля - status/created остаются дефолтными")
        void mapsRequestFieldsAndKeepsIgnoredDefaults() {
            // given
            var before = Instant.now();
            var request = new CreateSessionRequest("Spring Boot", "Java-разработчик", TrainingSession.Level.MIDDLE);

            // when
            TrainingSession entity = mapper.toEntity(request);

            // then
            assertThat(entity.getSkill()).isEqualTo("Spring Boot");
            assertThat(entity.getProfession()).isEqualTo("Java-разработчик");
            assertThat(entity.getLevel()).isEqualTo(TrainingSession.Level.MIDDLE);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getCompletedAt()).isNull();
            assertThat(entity.getQuestions()).isNull();
            assertThat(entity.getReport()).isNull();
            assertThat(entity.getStatus()).isEqualTo(TrainingSession.Status.CREATED);
            assertThat(entity.getCreated()).isCloseTo(before, within(1, ChronoUnit.MINUTES));
        }
    }

    @Nested
    @DisplayName("ToResponse")
    class ToResponse {

        @Test
        @DisplayName("Переносит все поля сессии, answeredCount и totalQuestions")
        void mapsAllSessionFieldsAnsweredCountAndTotalQuestions() {
            // given
            var sessionId = UUID.randomUUID();
            var created = Instant.now().minusSeconds(60);
            var completedAt = Instant.now();
            var session = TrainingSession.builder()
                    .id(sessionId)
                    .skill("Spring Boot")
                    .profession("Java-разработчик")
                    .level(TrainingSession.Level.SENIOR)
                    .status(TrainingSession.Status.COMPLETED)
                    .created(created)
                    .completedAt(completedAt)
                    .build();

            // when
            TrainingSessionResponse dto = mapper.toResponse(session, 3, 10);

            // then
            assertThat(dto.id()).isEqualTo(sessionId);
            assertThat(dto.skill()).isEqualTo("Spring Boot");
            assertThat(dto.profession()).isEqualTo("Java-разработчик");
            assertThat(dto.level()).isEqualTo(TrainingSession.Level.SENIOR);
            assertThat(dto.status()).isEqualTo(TrainingSession.Status.COMPLETED);
            assertThat(dto.answeredCount()).isEqualTo(3);
            assertThat(dto.totalQuestions()).isEqualTo(10);
            assertThat(dto.created()).isEqualTo(created);
            assertThat(dto.completedAt()).isEqualTo(completedAt);
        }
    }
}
