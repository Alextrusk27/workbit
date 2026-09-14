package ru.workbit.llm.dto;

import java.util.List;

public record LlmInterviewVacancy(
        String name,
        String employer,
        String experience,
        List<String> keySkills,
        String description
) {
}
