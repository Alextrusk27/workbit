package ru.workbit.interview.model.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.workbit.interview.dto.InterviewSessionResponse;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.model.VacancySnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewSessionMapperTest")
class InterviewSessionMapperTest {

    private final InterviewSessionMapper mapper = new InterviewSessionMapperImpl();

    private InterviewSession.InterviewSessionBuilder aSession() {
        return InterviewSession.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .vacancySnapshotId(UUID.randomUUID())
                .status(InterviewSession.Status.IN_PROGRESS)
                .totalQuestions(10);
    }

    @Nested
    @DisplayName("ToResponse")
    class ToResponse {

        @Test
        @DisplayName("С VacancyData переносит название/работодателя/ссылку/опыт из вакансии и answeredCount из параметра")
        void mapsFieldsFromVacancyDataOnCreate() {
            // given
            var created = Instant.now().minusSeconds(60);
            var session = aSession()
                    .created(created)
                    .build();
            var vacancyData = new VacancyData(
                    VacancySnapshot.Source.HH,
                    "123456",
                    "https://hh.ru/vacancy/123456",
                    "Java-разработчик",
                    "ООО Ромашка",
                    "От 1 года до 3 лет",
                    List.of("Java", "Spring"),
                    "Описание вакансии"
            );

            // when
            InterviewSessionResponse dto = mapper.toResponse(session, vacancyData, 4);

            // then
            assertThat(dto.id()).isEqualTo(session.getId());
            assertThat(dto.vacancyName()).isEqualTo("Java-разработчик");
            assertThat(dto.employer()).isEqualTo("ООО Ромашка");
            assertThat(dto.vacancyUrl()).isEqualTo("https://hh.ru/vacancy/123456");
            assertThat(dto.experience()).isEqualTo("От 1 года до 3 лет");
            assertThat(dto.status()).isEqualTo(InterviewSession.Status.IN_PROGRESS);
            assertThat(dto.answeredCount()).isEqualTo(4);
            assertThat(dto.totalQuestions()).isEqualTo(10);
            assertThat(dto.created()).isEqualTo(created);
            assertThat(dto.completedAt()).isNull();
        }

        @Test
        @DisplayName("С VacancySnapshotView переносит название/работодателя/ссылку/опыт из снапшота и answeredCount из параметра")
        void mapsFieldsFromVacancySnapshotViewOnRead() {
            // given
            var created = Instant.now().minusSeconds(120);
            var completedAt = Instant.now();
            var session = aSession()
                    .status(InterviewSession.Status.COMPLETED)
                    .created(created)
                    .completedAt(completedAt)
                    .build();
            var snapshotView = new VacancySnapshotView(
                    "654321",
                    "Python-разработчик",
                    "ООО Лютик",
                    "https://hh.ru/vacancy/654321",
                    "От 3 до 6 лет"
            );

            // when
            InterviewSessionResponse dto = mapper.toResponse(session, snapshotView, 7);

            // then
            assertThat(dto.id()).isEqualTo(session.getId());
            assertThat(dto.vacancyName()).isEqualTo("Python-разработчик");
            assertThat(dto.employer()).isEqualTo("ООО Лютик");
            assertThat(dto.vacancyUrl()).isEqualTo("https://hh.ru/vacancy/654321");
            assertThat(dto.experience()).isEqualTo("От 3 до 6 лет");
            assertThat(dto.status()).isEqualTo(InterviewSession.Status.COMPLETED);
            assertThat(dto.answeredCount()).isEqualTo(7);
            assertThat(dto.totalQuestions()).isEqualTo(10);
            assertThat(dto.created()).isEqualTo(created);
            assertThat(dto.completedAt()).isEqualTo(completedAt);
        }
    }
}
