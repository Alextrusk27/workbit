package ru.workbit.vacancy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record VacancyStatusesRequest(
        @Schema(description = "Ссылки на вакансии hh.ru", example = "[\"https://hh.ru/vacancy/123456\"]")
        @NotEmpty
        @Size(max = 100)
        List<@NotBlank String> urls
) {
}
