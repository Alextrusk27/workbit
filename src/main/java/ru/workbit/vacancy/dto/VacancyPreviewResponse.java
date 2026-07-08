package ru.workbit.vacancy.dto;

public record VacancyPreviewResponse(
        String name,
        String employer,
        String salary,
        String experience,
        String url
) {
}
