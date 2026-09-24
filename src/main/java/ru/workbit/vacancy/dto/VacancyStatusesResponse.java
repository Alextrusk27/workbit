package ru.workbit.vacancy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record VacancyStatusesResponse(
        @Schema(description = "Статус по каждой ссылке из запроса; ссылки, по которым hh.ru не ответил, отсутствуют")
        Map<String, VacancyStatusResponse.Status> statuses
) {
}
