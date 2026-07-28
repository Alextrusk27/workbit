package ru.workbit.interview.model.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.workbit.interview.dto.InterviewQuestionResponse;
import ru.workbit.interview.model.InterviewFeedback;
import ru.workbit.interview.model.InterviewQuestion;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewQuestionMapperTest")
class InterviewQuestionMapperTest {

    private final InterviewQuestionMapper mapper = new InterviewQuestionMapperImpl();

    private InterviewQuestion.InterviewQuestionBuilder aQuestion() {
        return InterviewQuestion.builder()
                .id(UUID.randomUUID())
                .text("Что такое JVM?")
                .orderIndex(2)
                .followUp(true)
                .answerText("JVM - виртуальная машина Java");
    }

    @Nested
    @DisplayName("ToDto")
    class ToDto {

        @Test
        @DisplayName("Переносит все поля вопроса, включая score/feedback из InterviewFeedback")
        void mapsAllFieldsWithFeedback() {
            // given
            var feedback = InterviewFeedback.builder()
                    .score(4)
                    .text("Хороший ответ, но не хватило деталей")
                    .build();
            var question = aQuestion().feedback(feedback).build();

            // when
            InterviewQuestionResponse dto = mapper.toDto(question);

            // then
            assertThat(dto.questionId()).isEqualTo(question.getId());
            assertThat(dto.questionText()).isEqualTo("Что такое JVM?");
            assertThat(dto.orderIndex()).isEqualTo(2);
            assertThat(dto.followUp()).isTrue();
            assertThat(dto.answerText()).isEqualTo("JVM - виртуальная машина Java");
            assertThat(dto.score()).isEqualTo(4);
            assertThat(dto.feedback()).isEqualTo("Хороший ответ, но не хватило деталей");
        }

        @Test
        @DisplayName("score и feedback остаются null, когда у вопроса нет InterviewFeedback")
        void scoreAndFeedbackNullWhenNoFeedback() {
            // given
            var question = aQuestion().feedback(null).build();

            // when
            InterviewQuestionResponse dto = mapper.toDto(question);

            // then
            assertThat(dto.score()).isNull();
            assertThat(dto.feedback()).isNull();
            assertThat(dto.questionId()).isEqualTo(question.getId());
            assertThat(dto.questionText()).isEqualTo("Что такое JVM?");
            assertThat(dto.orderIndex()).isEqualTo(2);
            assertThat(dto.followUp()).isTrue();
            assertThat(dto.answerText()).isEqualTo("JVM - виртуальная машина Java");
        }

        @Test
        @DisplayName("followUp = false для основного вопроса")
        void followUpFalseForMainQuestion() {
            // given
            var question = aQuestion().followUp(false).feedback(null).build();

            // when
            InterviewQuestionResponse dto = mapper.toDto(question);

            // then
            assertThat(dto.followUp()).isFalse();
        }
    }
}
