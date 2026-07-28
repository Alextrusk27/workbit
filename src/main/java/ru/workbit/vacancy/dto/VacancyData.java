package ru.workbit.vacancy.dto;

import ru.workbit.vacancy.model.VacancySnapshot;

import java.util.List;

public record VacancyData(
        VacancySnapshot.Source source,
        String sourceId,
        String url,
        String name,
        String employer,
        String experience,
        List<String> keySkills,
        String description
) {
}
