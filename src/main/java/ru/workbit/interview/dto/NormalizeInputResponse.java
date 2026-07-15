package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record NormalizeInputResponse(
        @Schema(description = "Удалось ли распознать профессию")
        boolean professionRecognized,

        @Schema(description = "Канонические варианты профессии для подтверждения пользователем, до 3-4; пустой список, если предлагать нечего")
        List<String> professionSuggestions,

        @Schema(description = "Удалось ли распознать тему; null, если тема не передавалась")
        Boolean topicRecognized,

        @Schema(description = "Канонические варианты темы; null, если тема не передавалась")
        List<String> topicSuggestions,

        @Schema(description = "Подходит ли тема профессии (мягкий сигнал, не блокировка); null, если тема не передавалась")
        Boolean topicFitsProfession
) {
}
