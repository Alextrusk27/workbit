package ru.workbit.llm.dto;

import java.util.List;

public record LlmInputNormalization(
        boolean skillRecognized,
        List<String> skillSuggestions,
        boolean professionRecognized,
        List<String> professionSuggestions,
        boolean skillFitsProfession
) {
}
