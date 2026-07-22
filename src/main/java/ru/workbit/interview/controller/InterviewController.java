package ru.workbit.interview.controller;

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
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.workbit.exception.dto.ApiError;
import ru.workbit.interview.dto.CreateInterviewSessionRequest;
import ru.workbit.interview.dto.InterviewQuestionResponse;
import ru.workbit.interview.dto.InterviewReportResponse;
import ru.workbit.interview.dto.InterviewSessionResponse;
import ru.workbit.interview.dto.SubmitAnswerBody;
import ru.workbit.interview.dto.SubmitAnswerRequest;
import ru.workbit.interview.service.InterviewService;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.util.annotation.Loggable;
import ru.workbit.util.annotation.Sensitive;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interview")
@Tag(name = "Interview", description = "AI-интервью по вакансии: сессии, вопросы, ответы, отчёт с оценкой вероятности оффера")
public class InterviewController {
    private final InterviewService interviewService;

    @PostMapping("/sessions")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Создать сессию интервью", description = "По ссылке на вакансию hh.ru загружает её данные, генерирует через LLM набор вопросов под вакансию и создаёт сессию интервью. Первый вопрос запрашивается отдельным вызовом.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сессия создана"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос или ссылка не является вакансией hh.ru", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Вакансия не найдена или в архиве", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "hh.ru или AI-сервис недоступны", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull InterviewSessionResponse> createSession(
            @RequestBody @Valid CreateInterviewSessionRequest request,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var session = interviewService.createSession(request.vacancyUrl(), userDetails.getId());
        return ResponseEntity
                .created(URI.create("/sessions/" + session.id()))
                .body(session);
    }

    @GetMapping("/sessions")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Список сессий интервью пользователя", description = "Возвращает сессии интервью текущего пользователя, новые первыми.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список сессий")
    })
    public ResponseEntity<@NotNull List<InterviewSessionResponse>> getAllSessions(
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(interviewService.getAll(userDetails.getId()));
    }

    @GetMapping("/sessions/{sessionId}")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Получить сессию по id", description = "Возвращает сессию интервью текущего пользователя.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сессия найдена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull InterviewSessionResponse> getSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(interviewService.get(sessionId, userDetails.getId()));
    }

    @PostMapping("/sessions/{sessionId}/questions/next")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Получить следующий вопрос", description = "Возвращает первый неотвеченный вопрос сессии по порядку. Вопросы генерируются заранее при создании сессии. Когда все вопросы отвечены, возвращает 409.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вопрос возвращён"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Неотвеченных вопросов не осталось или сессия уже завершена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull InterviewQuestionResponse> nextQuestion(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(interviewService.nextQuestion(sessionId, userDetails.getId()));
    }

    @PostMapping("/sessions/{sessionId}/questions/{questionId}")
    @Loggable(logArgs = true)
    @Operation(summary = "Отправить ответ на вопрос", description = "Сохраняет текст ответа на вопрос. Оценка по ходу интервью не выдаётся: фидбэк формируется только при завершении.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ответ сохранён"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Вопрос принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Вопрос не найден", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Вопрос уже отвечен, не принадлежит указанной сессии либо сессия завершена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> submitAnswer(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @RequestBody @Valid @Sensitive SubmitAnswerBody request,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        interviewService.submitAnswer(
                new SubmitAnswerRequest(userDetails.getId(), sessionId, questionId, request.answerText()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{sessionId}/finish")
    @Loggable(logArgs = true)
    @Operation(summary = "Завершить интервью", description = "Завершает интервью, запрашивает у LLM поразборный фидбэк по каждому ответу и формирует итоговый отчёт с оценкой вероятности оффера. Доступно после ответа минимум на 3 вопроса.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Отчёт сформирован"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Отвечено меньше 3 вопросов или сессия уже завершена", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull InterviewReportResponse> finishSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var report = interviewService.createReport(sessionId, userDetails.getId());
        return ResponseEntity
                .created(URI.create("/sessions/" + sessionId + "/report"))
                .body(report);
    }

    @GetMapping("/sessions/{sessionId}/report")
    @Loggable(logArgs = true)
    @Operation(summary = "Получить отчёт по интервью", description = "Возвращает ранее сформированный отчёт по завершённому интервью, включая поразборный фидбэк по каждому вопросу и вероятность оффера.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отчёт найден"),
            @ApiResponse(responseCode = "404", description = "Сессия или отчёт не найдены", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull InterviewReportResponse> getReport(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(interviewService.getReport(sessionId, userDetails.getId()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Loggable(logArgs = true)
    @Operation(summary = "Удалить сессию", description = "Удаляет сессию интервью вместе с вопросами, ответами и отчётом.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Сессия удалена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> deleteSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        interviewService.delete(sessionId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
