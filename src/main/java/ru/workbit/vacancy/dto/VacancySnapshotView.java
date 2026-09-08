package ru.workbit.vacancy.dto;

import java.util.List;

public record VacancySnapshotView(
        String sourceId,
        String name,
        String employer,
        String employerLogoUrl,
        String url,
        String experience,
        List<String> keySkills,
        String description
) {
}
