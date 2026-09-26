package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record InterviewQuestionResponse(
        @Schema(description = "Идентификатор вопроса")
        UUID questionId,

        @Schema(description = "Порядковый номер (1-based): у основного вопроса - в сессии, "
                + "у уточняющего - внутри своего вопроса", example = "1")
        int orderIndex,

        @Schema(description = "Текст вопроса")
        String questionText,

        @Schema(description = "Признак уточняющего вопроса к предыдущему ответу; в счётчик основных вопросов не входит")
        boolean followUp,

        @Schema(description = "Текст ответа пользователя, null пока не отвечен")
        String answerText,

        @Schema(description = "Оценка ответа от LLM (1-5); null у уточняющих вопросов и до отчёта", example = "4")
        Integer score,

        @Schema(description = "Текстовый фидбэк по ответу от LLM; null у уточняющих вопросов и до отчёта")
        String feedback
) {
}
