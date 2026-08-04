package ru.workbit.training.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.workbit.exception.ConflictException;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingSession;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TrainingSessionsTest")
class TrainingSessionsTest {

    private static TrainingQuestion aQuestion(int orderIndex, boolean answered) {
        return TrainingQuestion.builder()
                .id(UUID.randomUUID())
                .text("Вопрос " + orderIndex)
                .orderIndex(orderIndex)
                .answered(answered)
                .build();
    }

    private static TrainingSession sessionWithQuestions(TrainingQuestion... questions) {
        return TrainingSession.builder()
                .id(UUID.randomUUID())
                .questions(List.of(questions))
                .build();
    }

    @Nested
    @DisplayName("AnsweredSorted")
    class AnsweredSorted {

        @Test
        @DisplayName("Оставляет только отвеченные вопросы, сортирует по orderIndex, независимо от порядка в исходном списке")
        void filtersAnsweredAndSortsByOrderIndexRegardlessOfListOrder() {
            // given
            TrainingQuestion answered3 = aQuestion(3, true);
            TrainingQuestion unanswered2 = aQuestion(2, false);
            TrainingQuestion answered1 = aQuestion(1, true);
            TrainingSession session = sessionWithQuestions(answered3, unanswered2, answered1);

            // when
            List<TrainingQuestion> result = TrainingSessions.answeredSorted(session);

            // then
            assertThat(result).containsExactly(answered1, answered3);
        }

        @Test
        @DisplayName("Нет отвеченных вопросов - пустой список")
        void noAnsweredQuestionsReturnsEmptyList() {
            // given
            TrainingSession session = sessionWithQuestions(aQuestion(1, false), aQuestion(2, false));

            // when
            List<TrainingQuestion> result = TrainingSessions.answeredSorted(session);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Пустой список вопросов - пустой список")
        void emptyQuestionsListReturnsEmptyList() {
            // given
            TrainingSession session = sessionWithQuestions();

            // when / then
            assertThat(TrainingSessions.answeredSorted(session)).isEmpty();
        }
    }

    @Nested
    @DisplayName("CheckSessionNotCompleted")
    class CheckSessionNotCompleted {

        @Test
        @DisplayName("Сессия завершена (COMPLETED) - ConflictException")
        void throwsWhenSessionCompleted() {
            // given
            TrainingSession session = TrainingSession.builder()
                    .id(UUID.randomUUID()).status(TrainingSession.Status.COMPLETED).build();

            // when / then
            assertThatThrownBy(() -> TrainingSessions.checkSessionNotCompleted(session))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
        }

        @ParameterizedTest
        @EnumSource(value = TrainingSession.Status.class, names = {"CREATED", "IN_PROGRESS"})
        @DisplayName("Сессия не завершена (CREATED/IN_PROGRESS) - исключения нет")
        void doesNotThrowForNonCompletedStatuses(TrainingSession.Status status) {
            // given
            TrainingSession session = TrainingSession.builder().id(UUID.randomUUID()).status(status).build();

            // when / then
            assertThatCode(() -> TrainingSessions.checkSessionNotCompleted(session)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("CheckSessionCompleted")
    class CheckSessionCompleted {

        @ParameterizedTest
        @EnumSource(value = TrainingSession.Status.class, names = {"CREATED", "IN_PROGRESS"})
        @DisplayName("Сессия не завершена (CREATED/IN_PROGRESS) - ConflictException")
        void throwsWhenSessionNotCompleted(TrainingSession.Status status) {
            // given
            TrainingSession session = TrainingSession.builder().id(UUID.randomUUID()).status(status).build();

            // when / then
            assertThatThrownBy(() -> TrainingSessions.checkSessionCompleted(session))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session is not finished");
        }

        @Test
        @DisplayName("Сессия завершена (COMPLETED) - исключения нет")
        void doesNotThrowForCompletedStatus() {
            // given
            TrainingSession session = TrainingSession.builder()
                    .id(UUID.randomUUID()).status(TrainingSession.Status.COMPLETED).build();

            // when / then
            assertThatCode(() -> TrainingSessions.checkSessionCompleted(session)).doesNotThrowAnyException();
        }
    }
}
