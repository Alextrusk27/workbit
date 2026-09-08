package ru.workbit.vacancy.dto;

import java.util.List;
import ru.workbit.vacancy.model.VacancySnapshot;

public record VacancyData(
        VacancySnapshot.Source source,
        String sourceId,
        String url,
        String name,
        String employer,
        String employerLogoUrl,
        String experience,
        List<String> keySkills,
        String description
) {
}
