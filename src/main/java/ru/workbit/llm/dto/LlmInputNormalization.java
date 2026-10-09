package ru.workbit.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmInputNormalization(
        boolean skillRecognized,
        List<String> skillSuggestions,
        boolean professionRecognized,
        List<String> professionSuggestions,
        boolean skillFitsProfession
) {
}
