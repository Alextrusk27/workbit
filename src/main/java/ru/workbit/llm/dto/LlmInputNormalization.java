package ru.workbit.llm.dto;

import java.util.List;

public record LlmInputNormalization(
        boolean professionRecognized,
        List<String> professionSuggestions,
        boolean topicRecognized,
        List<String> topicSuggestions,
        boolean topicFitsProfession
) {
}
