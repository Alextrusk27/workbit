package ru.workbit.vacancy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record VacancyStatusResponse(
        @Schema(description = "Текущее состояние вакансии на площадке")
        Status status
) {
    public enum Status {
        ACTIVE, ARCHIVED, NOT_FOUND
    }
}
