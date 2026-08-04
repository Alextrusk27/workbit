package ru.workbit.llm.dto;

import java.util.List;

public record LlmInputNormalizationRequest(
        String skill,
        String profession,
        List<String> knownSkills,
        List<String> knownProfessions
) {
}
