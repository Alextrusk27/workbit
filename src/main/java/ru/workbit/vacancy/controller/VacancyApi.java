package ru.workbit.vacancy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.workbit.vacancy.dto.VacancyPreviewResponse;
import ru.workbit.vacancy.dto.VacancyStatusResponse;
import ru.workbit.vacancy.dto.VacancyStatusesRequest;
import ru.workbit.vacancy.dto.VacancyStatusesResponse;

@RequestMapping("/api/v1/vacancies")
@Tag(name = "Vacancy", description = "Получение данных о вакансии с hh.ru")
public interface VacancyApi {

    @Operation(summary = "Предпросмотр вакансии",
            description = "По ссылке на вакансию hh.ru возвращает краткую сводку.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сводка по вакансии"),
            @ApiResponse(responseCode = "400",
                    description = "Отсутствует параметр url или ссылка не является вакансией hh.ru"),
            @ApiResponse(responseCode = "404", description = "Вакансия не найдена или в архиве"),
            @ApiResponse(responseCode = "503", description = "hh.ru недоступен")
    })
    @GetMapping("/preview")
    VacancyPreviewResponse preview(
            @Parameter(description = "Ссылка на вакансию hh.ru", example = "https://hh.ru/vacancy/123456")
            @RequestParam String url
    );

    @Operation(summary = "Статус вакансии",
            description = "Проверяет по ссылке, доступна ли вакансия на hh.ru; статус кешируется на сервере 30 минут.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус вакансии"),
            @ApiResponse(responseCode = "400",
                    description = "Отсутствует параметр url или ссылка не является вакансией hh.ru"),
            @ApiResponse(responseCode = "503", description = "hh.ru недоступен")
    })
    @GetMapping("/status")
    VacancyStatusResponse status(
            @Parameter(description = "Ссылка на вакансию hh.ru", example = "https://hh.ru/vacancy/123456")
            @RequestParam String url
    );

    @Operation(summary = "Статусы нескольких вакансий",
            description = "Пакетный аналог /status: возвращает статус каждой вакансии из списка ссылок (до 100).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статусы вакансий по ссылкам"),
            @ApiResponse(responseCode = "400",
                    description = "Список пуст, длиннее 100 ссылок или содержит ссылку не на вакансию hh.ru")
    })
    @PostMapping("/statuses")
    VacancyStatusesResponse statuses(@RequestBody @Valid VacancyStatusesRequest request);
}
