package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record TrainingQuestionResponse(
        @Schema(description = "Идентификатор вопроса")
        UUID questionId,

        @Schema(description = "Порядковый индекс вопроса в сессии (1-based)", example = "1")
        int orderIndex,

        @Schema(description = "Текст вопроса")
        String questionText,

        @Schema(description = "Признак уточняющего вопроса к предыдущему ответу; в счётчик основных вопросов не входит")
        boolean followUp,

        @Schema(description = "Текст ответа пользователя, null пока не отвечен")
        String answerText,

        @Schema(description = "Оценка ответа от LLM (1-5), null пока не выставлена", example = "4")
        Integer score,

        @Schema(description = "Текстовый фидбэк по ответу от LLM, null пока не выставлен")
        String feedback

) implements QuestionResponse {
}
