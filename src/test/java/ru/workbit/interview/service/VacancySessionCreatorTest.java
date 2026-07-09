package ru.workbit.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.interview.model.Category;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.SessionSource;
import ru.workbit.interview.repository.SessionRepository;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.service.VacancyService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VacancySessionCreatorTest")
class VacancySessionCreatorTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    VacancyService vacancyService;
    @Mock
    SessionRepository sessionRepository;

    @InjectMocks
    VacancySessionCreator vacancySessionCreator;

    @Nested
    @DisplayName("Persist")
    class Persist {

        @Test
        @DisplayName("Сохраняет снапшот вакансии и создаёт сессию с вопросами категории VACANCY по порядку")
        void savesSnapshotAndBuildsSessionWithOrderedQuestions() {
            // given
            VacancyData data = new VacancyData(123L, "https://hh.ru/vacancy/123", "Java-разработчик",
                    "ООО Ромашка", "От 3 до 6 лет", List.of("Java", "Spring"), "Описание вакансии");
            UUID snapshotId = UUID.randomUUID();
            when(vacancyService.saveSnapshot(data, "Java-разработчик")).thenReturn(snapshotId);
            when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

            List<String> questionTexts = List.of("Q1", "Q2", "Q3");

            // when
            InterviewSession result = vacancySessionCreator.persist(data, "Java-разработчик", questionTexts, USER_ID);

            // then
            assertThat(result.getSource()).isEqualTo(SessionSource.VACANCY);
            assertThat(result.getVacancySnapshotId()).isEqualTo(snapshotId);
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getTotalQuestions()).isEqualTo(3);

            assertThat(result.getQuestions()).hasSize(3);
            assertThat(result.getQuestions())
                    .extracting("questionText")
                    .containsExactly("Q1", "Q2", "Q3");
            assertThat(result.getQuestions())
                    .extracting("orderIndex")
                    .containsExactly(1, 2, 3);
            assertThat(result.getQuestions())
                    .allSatisfy(q -> assertThat(q.getCategory()).isEqualTo(Category.VACANCY));
            assertThat(result.getQuestions())
                    .allSatisfy(q -> assertThat(q.getSession()).isSameAs(result));

            var captor = ArgumentCaptor.forClass(InterviewSession.class);
            verify(sessionRepository).save(captor.capture());
            assertThat(captor.getValue()).isSameAs(result);
        }

        @Test
        @DisplayName("Строит сессию без вопросов, когда список вопросов пуст")
        void buildsSessionWithNoQuestionsWhenListEmpty() {
            // given
            VacancyData data = new VacancyData(null, null, null, null, null, null, "Текст вакансии");
            UUID snapshotId = UUID.randomUUID();
            when(vacancyService.saveSnapshot(eq(data), eq("Вакансия"))).thenReturn(snapshotId);
            when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            InterviewSession result = vacancySessionCreator.persist(data, "Вакансия", List.of(), USER_ID);

            // then
            assertThat(result.getQuestions()).isEmpty();
            assertThat(result.getTotalQuestions()).isZero();
        }
    }
}
