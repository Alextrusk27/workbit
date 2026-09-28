package ru.workbit.interview.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.workbit.interview.dto.CreateInterviewSessionRequest;
import ru.workbit.interview.dto.FeedbackRequest;
import ru.workbit.interview.dto.InterviewQuestionResponse;
import ru.workbit.interview.dto.InterviewReportResponse;
import ru.workbit.interview.dto.InterviewSessionResponse;
import ru.workbit.interview.dto.InterviewVacancyDetailResponse;
import ru.workbit.interview.dto.InterviewVacancyResponse;
import ru.workbit.interview.dto.SubmitAnswerBody;
import ru.workbit.security.model.CustomUserDetails;

@RequestMapping("/api/v1/interview")
@Tag(
        name = "Interview",
        description = "AI-интервью по вакансии: сессии, вопросы, ответы, отчёт с оценкой вероятности оффера")
public interface InterviewApi {

    @Operation(summary = "Создать сессию интервью",
            description = "Загружает вакансию с hh.ru и планирует вопросы через LLM.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сессия создана"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос или ссылка не на вакансию hh.ru"),
            @ApiResponse(responseCode = "402", description = "Не хватает лимитов"),
            @ApiResponse(responseCode = "404", description = "Вакансия не найдена или в архиве"),
            @ApiResponse(responseCode = "409", description = "По этой вакансии уже есть незавершённое интервью"),
            @ApiResponse(responseCode = "503", description = "hh.ru или AI-сервис недоступны")
    })
    @PostMapping("/sessions")
    ResponseEntity<InterviewSessionResponse> createSession(
            @RequestBody @Valid CreateInterviewSessionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Получить сессию по id")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сессия найдена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена")
    })
    @GetMapping("/sessions/{sessionId}")
    ResponseEntity<InterviewSessionResponse> getSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "История отвеченных вопросов")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "История возвращена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена")
    })
    @GetMapping("/sessions/{sessionId}/questions")
    ResponseEntity<List<InterviewQuestionResponse>> answeredQuestions(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Получить следующий вопрос",
            description = "Если неотвеченных вопросов нет, запрашивает у LLM следующий вопрос беседы.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вопрос возвращён"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена"),
            @ApiResponse(responseCode = "409", description = "Беседа окончена или сессия уже завершена"),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен")
    })
    @PostMapping("/sessions/{sessionId}/questions/next")
    ResponseEntity<InterviewQuestionResponse> nextQuestion(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Отправить ответ на вопрос")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ответ сохранён"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос"),
            @ApiResponse(responseCode = "403", description = "Вопрос принадлежит другому пользователю"),
            @ApiResponse(responseCode = "404", description = "Вопрос не найден"),
            @ApiResponse(
                    responseCode = "409",
                    description = "Вопрос уже отвечен, не принадлежит сессии либо сессия завершена")
    })
    @PostMapping("/sessions/{sessionId}/questions/{questionId}")
    ResponseEntity<Void> submitAnswer(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @RequestBody @Valid SubmitAnswerBody request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Оценить разбор вопроса")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Отзыв сохранён"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос"),
            @ApiResponse(responseCode = "403", description = "Вопрос принадлежит другому пользователю"),
            @ApiResponse(responseCode = "404", description = "Вопрос не найден"),
            @ApiResponse(responseCode = "409", description = "Вопрос не принадлежит указанной сессии")
    })
    @PostMapping("/sessions/{sessionId}/questions/{questionId}/feedback")
    ResponseEntity<Void> submitQuestionFeedback(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @RequestBody @Valid FeedbackRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Оценить итоговый отчёт")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Отзыв сохранён"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос"),
            @ApiResponse(responseCode = "404", description = "Сессия или отчёт не найдены")
    })
    @PostMapping("/sessions/{sessionId}/report/feedback")
    ResponseEntity<Void> submitReportFeedback(
            @PathVariable UUID sessionId,
            @RequestBody @Valid FeedbackRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Завершить интервью",
            description = "Запрашивает у LLM фидбэк по ответам и формирует итоговый отчёт. Идемпотентен.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Отчёт сформирован"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена"),
            @ApiResponse(responseCode = "409", description = "Отвечены не все основные вопросы"),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен")
    })
    @PostMapping("/sessions/{sessionId}/finish")
    ResponseEntity<InterviewReportResponse> finishSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Получить отчёт по интервью")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отчёт найден"),
            @ApiResponse(responseCode = "404", description = "Сессия или отчёт не найдены")
    })
    @GetMapping("/sessions/{sessionId}/report")
    ResponseEntity<InterviewReportResponse> getReport(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Список вакансий пользователя",
            description = "Группирует интервью пользователя по вакансиям.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Список вакансий")
    @GetMapping("/vacancies")
    ResponseEntity<List<InterviewVacancyResponse>> getAllVacancies(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Детали вакансии")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вакансия найдена"),
            @ApiResponse(responseCode = "404", description = "У пользователя нет интервью по этой вакансии")
    })
    @GetMapping("/vacancies/{vacancyId}")
    ResponseEntity<InterviewVacancyDetailResponse> getVacancy(
            @PathVariable String vacancyId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Удалить вакансию",
            description = "Удаляет интервью пользователя по вакансии со всеми вопросами, ответами и отчётами.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Вакансия удалена"),
            @ApiResponse(responseCode = "404", description = "У пользователя нет интервью по этой вакансии")
    })
    @DeleteMapping("/vacancies/{vacancyId}")
    ResponseEntity<Void> deleteVacancy(
            @PathVariable String vacancyId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );
}
