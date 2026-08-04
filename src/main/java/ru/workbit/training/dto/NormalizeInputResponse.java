package ru.workbit.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record NormalizeInputResponse(
        @Schema(description = "Удалось ли распознать навык")
        boolean skillRecognized,

        @Schema(description = "Канонические варианты навыка для подтверждения пользователем, до 3-4; пустой список, если предлагать нечего")
        List<String> skillSuggestions,

        @Schema(description = "Удалось ли распознать профессию")
        boolean professionRecognized,

        @Schema(description = "Канонические варианты профессии для подтверждения пользователем, до 3-4; пустой список, если предлагать нечего")
        List<String> professionSuggestions,

        @Schema(description = "Подходит ли навык профессии (мягкий сигнал, не блокировка)")
        boolean skillFitsProfession
) {
}
