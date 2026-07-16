package ru.workbit.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.workbit.interview.model.TrainingQuestion;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrainingCasesTest")
class TrainingCasesTest {

    @Nested
    @DisplayName("GroupCases")
    class GroupCases {

        @Test
        @DisplayName("Уточнения группируются с основным вопросом по parentQuestionId (сортировка по orderIndex), независимо от порядка в исходном списке")
        void groupsFollowUpsByParentQuestionIdRegardlessOfListOrder() {
            // given
            UUID main1Id = UUID.randomUUID();
            UUID main2Id = UUID.randomUUID();
            TrainingQuestion main1 = TrainingQuestion.builder().id(main1Id).orderIndex(1).followUp(false).build();
            TrainingQuestion main2 = TrainingQuestion.builder().id(main2Id).orderIndex(2).followUp(false).build();
            TrainingQuestion main1FollowUp2 = TrainingQuestion.builder()
                    .parentQuestionId(main1Id).orderIndex(2).followUp(true).build();
            TrainingQuestion main1FollowUp1 = TrainingQuestion.builder()
                    .parentQuestionId(main1Id).orderIndex(1).followUp(true).build();
            TrainingQuestion main2FollowUp1 = TrainingQuestion.builder()
                    .parentQuestionId(main2Id).orderIndex(1).followUp(true).build();

            // перемешанный порядок в исходном списке
            List<TrainingQuestion> answered = List.of(main2FollowUp1, main2, main1FollowUp2, main1, main1FollowUp1);

            // when
            List<List<TrainingQuestion>> result = TrainingCases.groupCases(answered);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsExactly(main1, main1FollowUp1, main1FollowUp2);
            assertThat(result.get(1)).containsExactly(main2, main2FollowUp1);
        }

        @Test
        @DisplayName("Основной вопрос без уточнений - кейс из одного элемента")
        void mainWithoutFollowUpsFormsSingleElementCase() {
            // given
            TrainingQuestion main = TrainingQuestion.builder().id(UUID.randomUUID()).orderIndex(1).followUp(false).build();

            // when
            List<List<TrainingQuestion>> result = TrainingCases.groupCases(List.of(main));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly(main);
        }

        @Test
        @DisplayName("Уточнение без своего основного вопроса в списке - не попадает ни в один кейс")
        void orphanFollowUpWithoutMainInListIsDropped() {
            // given
            TrainingQuestion main = TrainingQuestion.builder().id(UUID.randomUUID()).orderIndex(1).followUp(false).build();
            TrainingQuestion orphanFollowUp = TrainingQuestion.builder()
                    .parentQuestionId(UUID.randomUUID()).orderIndex(1).followUp(true).build();

            // when
            List<List<TrainingQuestion>> result = TrainingCases.groupCases(List.of(main, orphanFollowUp));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly(main);
        }

        @Test
        @DisplayName("Пустой список отвеченных - пустой список кейсов")
        void emptyAnsweredListReturnsEmptyCases() {
            // when / then
            assertThat(TrainingCases.groupCases(List.of())).isEmpty();
        }
    }
}
