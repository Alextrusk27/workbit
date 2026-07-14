package ru.workbit.vacancy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record VacancyPreviewResponse(
        @Schema(description = "Название вакансии", example = "Java-разработчик")
        String name,

        @Schema(description = "Название работодателя", example = "ООО Ромашка")
        String employer,

        @Schema(description = "Зарплата в виде строки, может отсутствовать", example = "150 000 - 250 000 ₽")
        String salary,

        @Schema(description = "Требуемый опыт работы", example = "От 1 года до 3 лет")
        String experience,

        @Schema(description = "Каноническая ссылка на вакансию hh.ru", example = "https://hh.ru/vacancy/123456")
        String url
) {
}
