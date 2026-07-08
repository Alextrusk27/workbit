package ru.workbit.vacancy.dto;

import java.util.List;

public record VacancyData(
        Long hhVacancyId,
        String url,
        String name,
        String employer,
        String experience,
        List<String> keySkills,
        String description
) {
}
