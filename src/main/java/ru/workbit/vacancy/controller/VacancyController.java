package ru.workbit.vacancy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.workbit.exception.dto.ApiError;
import ru.workbit.util.annotation.Loggable;
import ru.workbit.vacancy.dto.VacancyPreviewResponse;
import ru.workbit.vacancy.dto.VacancyStatusResponse;
import ru.workbit.vacancy.dto.VacancyStatusesRequest;
import ru.workbit.vacancy.dto.VacancyStatusesResponse;
import ru.workbit.vacancy.service.VacancyService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vacancies")
@Tag(name = "Vacancy", description = "Получение данных о вакансии с hh.ru")
public class VacancyController {
    private final VacancyService vacancyService;

    @GetMapping("/preview")
    @Loggable(logArgs = true, logResult = true)
    @Operation(
            summary = "Предпросмотр вакансии",
            description = "По ссылке на вакансию hh.ru возвращает краткую сводку: название, работодателя, зарплату и "
            + "требуемый опыт.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сводка по вакансии"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Отсутствует параметр url или ссылка не является вакансией hh.ru",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Нет токена или токен недействителен",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Вакансия не найдена или в архиве",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "hh.ru недоступен",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public VacancyPreviewResponse preview(
            @Parameter(description = "Ссылка на вакансию hh.ru", example = "https://hh.ru/vacancy/123456")
            @RequestParam String url) {
        return vacancyService.preview(url);
    }

    @GetMapping("/status")
    @Loggable(logArgs = true, logResult = true)
    @Operation(
            summary = "Текущий статус вакансии",
            description = "Проверяет по ссылке, доступна ли вакансия на hh.ru: активна, в архиве или удалена. "
            + "Результат кешируется на сервере на 30 минут.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус вакансии"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Отсутствует параметр url или ссылка не является вакансией hh.ru",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Нет токена или токен недействителен",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "hh.ru недоступен",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public VacancyStatusResponse status(
            @Parameter(description = "Ссылка на вакансию hh.ru", example = "https://hh.ru/vacancy/123456")
            @RequestParam String url) {
        return vacancyService.getStatus(url);
    }

    @PostMapping("/statuses")
    @Loggable(logArgs = true, logResult = true)
    @Operation(
            summary = "Текущие статусы нескольких вакансий",
            description = "Пакетный вариант /status: по списку ссылок (до 100) возвращает статус каждой вакансии на "
            + "hh.ru. Результаты кешируются на сервере на 30 минут. Если hh.ru недоступен, ссылки без свежего "
            + "статуса в кеше в ответ не попадают.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статусы вакансий по ссылкам"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Список пуст, длиннее 100 ссылок или содержит ссылку не на вакансию hh.ru",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Нет токена или токен недействителен",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public VacancyStatusesResponse statuses(@RequestBody @Valid VacancyStatusesRequest request) {
        return vacancyService.getStatuses(request.urls());
    }
}
