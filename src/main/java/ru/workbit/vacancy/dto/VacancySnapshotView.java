package ru.workbit.vacancy.dto;

public record VacancySnapshotView(
        String sourceId,
        String name,
        String employer,
        String url,
        String experience
) {
}
