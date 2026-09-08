package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import ru.workbit.interview.model.InterviewSession;

public record InterviewSessionResponse(
        @Schema(description = "Идентификатор сессии")
        UUID id,

        @Schema(description = "Идентификатор вакансии", example = "123456")
        String vacancyId,

        @Schema(description = "Название вакансии", example = "Java-разработчик")
        String vacancyName,

        @Schema(description = "Название работодателя", example = "ООО Ромашка")
        String employer,

        @Schema(
                description = "Ссылка на логотип работодателя, null если его нет в вакансии",
                example = "https://img.hhcdn.ru/employer-logo/1234567.png")
        String employerLogoUrl,

        @Schema(description = "Ссылка на вакансию", example = "https://hh.ru/vacancy/123456")
        String vacancyUrl,

        @Schema(description = "Требуемый опыт работы из вакансии, null если не указан", example = "От 1 года до 3 лет")
        String experience,

        @Schema(description = "Статус сессии")
        InterviewSession.Status status,

        @Schema(
                description = "Количество основных вопросов, на которые уже дан ответ (уточняющие не считаются)",
                example = "3")
        int answeredCount,

        @Schema(description = "Общее количество основных вопросов интервью", example = "10")
        int totalQuestions,

        @Schema(description = "Момент создания сессии")
        Instant created,

        @Schema(description = "Момент завершения сессии, null пока сессия не завершена")
        Instant completedAt,

        @Schema(
                description = "Прощальная реплика интервьюера в конце беседы; null, пока беседа идёт",
                example = "На этом всё, спасибо за разговор. Дальше будет разбор ответов.")
        String closingRemark
) {
}
