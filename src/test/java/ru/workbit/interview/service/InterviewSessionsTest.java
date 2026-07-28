package ru.workbit.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.workbit.exception.ConflictException;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("InterviewSessionsTest")
class InterviewSessionsTest {

    @Nested
    @DisplayName("AnsweredSorted")
    class AnsweredSorted {

        @Test
        @DisplayName("Возвращает только отвеченные вопросы, отсортированные по orderIndex, независимо от порядка в исходном списке")
        void filtersAnsweredAndSortsByOrderIndex() {
            // given
            InterviewQuestion answered3 = InterviewQuestion.builder().orderIndex(3).answered(true).build();
            InterviewQuestion unanswered2 = InterviewQuestion.builder().orderIndex(2).answered(false).build();
            InterviewQuestion answered1 = InterviewQuestion.builder().orderIndex(1).answered(true).build();
            InterviewSession session = InterviewSession.builder()
                    .questions(List.of(answered3, unanswered2, answered1)).build();

            // when
            List<InterviewQuestion> result = InterviewSessions.answeredSorted(session);

            // then
            assertThat(result).containsExactly(answered1, answered3);
        }

        @Test
        @DisplayName("Пустой список вопросов - пустой результат")
        void emptyQuestionsReturnsEmptyList() {
            // given
            InterviewSession session = InterviewSession.builder().questions(List.of()).build();

            // when / then
            assertThat(InterviewSessions.answeredSorted(session)).isEmpty();
        }
    }

    @Nested
    @DisplayName("GroupCases")
    class GroupCases {

        @Test
        @DisplayName("Уточнения группируются с основным вопросом по parentQuestionId (сортировка по orderIndex), независимо от порядка в исходном списке")
        void groupsFollowUpsByParentQuestionIdRegardlessOfListOrder() {
            // given
            UUID main1Id = UUID.randomUUID();
            UUID main2Id = UUID.randomUUID();
            InterviewQuestion main1 = InterviewQuestion.builder().id(main1Id).orderIndex(1).followUp(false).build();
            InterviewQuestion main2 = InterviewQuestion.builder().id(main2Id).orderIndex(2).followUp(false).build();
            InterviewQuestion main1FollowUp2 = InterviewQuestion.builder()
                    .parentQuestionId(main1Id).orderIndex(2).followUp(true).build();
            InterviewQuestion main1FollowUp1 = InterviewQuestion.builder()
                    .parentQuestionId(main1Id).orderIndex(1).followUp(true).build();
            InterviewQuestion main2FollowUp1 = InterviewQuestion.builder()
                    .parentQuestionId(main2Id).orderIndex(1).followUp(true).build();

            // перемешанный порядок в исходном списке
            List<InterviewQuestion> answered = List.of(main2FollowUp1, main2, main1FollowUp2, main1, main1FollowUp1);

            // when
            List<List<InterviewQuestion>> result = InterviewSessions.groupCases(answered);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsExactly(main1, main1FollowUp1, main1FollowUp2);
            assertThat(result.get(1)).containsExactly(main2, main2FollowUp1);
        }

        @Test
        @DisplayName("Основной вопрос без уточнений - кейс из одного элемента")
        void mainWithoutFollowUpsFormsSingleElementCase() {
            // given
            InterviewQuestion main = InterviewQuestion.builder().id(UUID.randomUUID()).orderIndex(1).followUp(false).build();

            // when
            List<List<InterviewQuestion>> result = InterviewSessions.groupCases(List.of(main));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly(main);
        }

        @Test
        @DisplayName("Уточнение без своего основного вопроса в списке - не попадает ни в один кейс")
        void orphanFollowUpWithoutMainInListIsDropped() {
            // given
            InterviewQuestion main = InterviewQuestion.builder().id(UUID.randomUUID()).orderIndex(1).followUp(false).build();
            InterviewQuestion orphanFollowUp = InterviewQuestion.builder()
                    .parentQuestionId(UUID.randomUUID()).orderIndex(1).followUp(true).build();

            // when
            List<List<InterviewQuestion>> result = InterviewSessions.groupCases(List.of(main, orphanFollowUp));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly(main);
        }

        @Test
        @DisplayName("Пустой список отвеченных - пустой список кейсов")
        void emptyAnsweredListReturnsEmptyCases() {
            // when / then
            assertThat(InterviewSessions.groupCases(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("CheckSessionNotCompleted")
    class CheckSessionNotCompleted {

        @Test
        @DisplayName("Сессия завершена - ConflictException")
        void throwsWhenSessionCompleted() {
            // given
            InterviewSession session = InterviewSession.builder()
                    .id(UUID.randomUUID()).status(InterviewSession.Status.COMPLETED).build();

            // when / then
            assertThatThrownBy(() -> InterviewSessions.checkSessionNotCompleted(session))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
        }

        @Test
        @DisplayName("Сессия не завершена (CREATED/IN_PROGRESS) - ничего не бросает")
        void doesNothingWhenSessionNotCompleted() {
            // given
            InterviewSession created = InterviewSession.builder()
                    .id(UUID.randomUUID()).status(InterviewSession.Status.CREATED).build();
            InterviewSession inProgress = InterviewSession.builder()
                    .id(UUID.randomUUID()).status(InterviewSession.Status.IN_PROGRESS).build();

            // when / then
            assertThatNoException().isThrownBy(() -> InterviewSessions.checkSessionNotCompleted(created));
            assertThatNoException().isThrownBy(() -> InterviewSessions.checkSessionNotCompleted(inProgress));
        }
    }
}
