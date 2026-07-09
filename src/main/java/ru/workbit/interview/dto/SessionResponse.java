package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.interview.model.CompanyType;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.Profession;
import ru.workbit.interview.model.SessionSource;
import ru.workbit.interview.model.SessionStatus;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        @Schema(description = "Идентификатор сессии")
        UUID id,

        @Schema(description = "Профессия, по которой проводится собеседование; null для сессий по вакансии", example = "Java-разработчик")
        Profession profession,

        @Schema(description = "Тип компании, под который стилизуются вопросы; null для сессий по вакансии", example = "Продуктовая компания")
        CompanyType companyType,

        @Schema(description = "Целевой уровень кандидата; null для сессий по вакансии", example = "Middle")
        Level level,

        @Schema(description = "Источник вопросов сессии: каталог или вакансия")
        SessionSource source,

        @Schema(description = "Данные вакансии для сессий по вакансии; null для каталожных сессий")
        VacancyInfo vacancy,

        @Schema(description = "Статус сессии")
        SessionStatus status,

        @Schema(description = "Общее количество вопросов в сессии", example = "10")
        int totalQuestions,

        @Schema(description = "Количество вопросов, на которые уже дан ответ", example = "3")
        int answeredCount,

        @Schema(description = "Момент создания сессии")
        Instant created,

        @Schema(description = "Момент завершения сессии, null пока сессия не завершена")
        Instant completedAt
) {
    public record VacancyInfo(
            @Schema(description = "Название вакансии", example = "Java-разработчик")
            String name,

            @Schema(description = "Работодатель; null при текстовом вводе вакансии", example = "ООО Ромашка")
            String employer,

            @Schema(description = "Ссылка на вакансию hh.ru; null при текстовом вводе", example = "https://hh.ru/vacancy/123456")
            String url,

            @Schema(description = "Требуемый опыт работы; null при текстовом вводе или если не указан в вакансии", example = "От 1 года до 3 лет")
            String experience
    ) {
    }
}
