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
import ru.workbit.interview.dto.*;
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
@Tag(name = "Interview", description = "Прохождение тренировочного собеседования: сессии, вопросы, ответы, отчёт")
public class InterviewController {
    private final InterviewService interviewService;

    @GetMapping("/options")
    @Loggable(logResult = true)
    @Operation(summary = "Справочник значений для создания сессии", description = "Возвращает допустимые профессии, уровни и типы компании, чтобы фронт не хардкодил лейблы enum'ов.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Справочник значений")
    })
    public ResponseEntity<@NotNull InterviewOptionsResponse> getOptions() {
        return ResponseEntity.ok(interviewService.getOptions());
    }

    @PostMapping("/sessions")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Создать сессию собеседования", description = "Создаёт новую сессию с набором вопросов по выбранной профессии, уровню и типу компании.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сессия создана"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Не удалось сформировать вопросы для сессии", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull SessionResponse> createSession(
            @RequestBody @Valid CreateSessionRequest request,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var session = interviewService.createSession(request, userDetails.getId());
        return ResponseEntity
                .created(URI.create("/sessions/" + session.id()))
                .body(session);
    }

    @GetMapping("/sessions")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Список сессий пользователя", description = "Возвращает все сессии собеседований текущего пользователя.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список сессий")
    })
    public ResponseEntity<@NotNull List<SessionResponse>> getAllSessions(
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                interviewService.getAllSessions(
                        userDetails.getId()
                )
        );
    }

    @GetMapping("/sessions/{sessionId}")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Получить сессию по id", description = "Возвращает сессию собеседования текущего пользователя.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сессия найдена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull SessionResponse> getSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                interviewService.getSession(
                        sessionId, userDetails.getId()
                )
        );
    }

    @GetMapping("/sessions/{sessionId}/continue")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Продолжить сессию", description = "Возвращает следующий неотвеченный вопрос сессии.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Следующий вопрос найден"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена или неотвеченных вопросов не осталось", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull QuestionResponse> continueSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                interviewService.continueSession(
                        sessionId, userDetails.getId()
                )
        );
    }

    @GetMapping("/sessions/{sessionId}/questions/{index}")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Получить вопрос по порядковому индексу", description = "Возвращает вопрос сессии по его порядковому номеру (1-based).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вопрос найден"),
            @ApiResponse(responseCode = "404", description = "Сессия или вопрос с таким индексом не найдены", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull QuestionResponse> getQuestion(
            @PathVariable UUID sessionId,
            @Parameter(description = "Порядковый индекс вопроса в сессии") @PathVariable int index,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                interviewService.getQuestion(
                        new QuestionRequest(
                                sessionId, index, userDetails.getId()
                        )
                )
        );
    }

    @PostMapping("/sessions/{sessionId}/questions/{questionId}")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Отправить ответ на вопрос", description = "Сохраняет текст ответа на вопрос. При evaluate=true дополнительно запускает LLM-оценку ответа.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ответ сохранён"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Вопрос принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Вопрос не найден", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Вопрос уже отвечен или не принадлежит указанной сессии", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull QuestionResponse> submitAnswer(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @RequestBody @Valid SubmitAnswerBody request,
            @Parameter(description = "Запустить LLM-оценку ответа") @RequestParam(required = false, defaultValue = "false") boolean evaluate,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                interviewService.submitAnswer(
                        new SubmitAnswerRequest(
                                userDetails.getId(), sessionId, questionId, evaluate, request.answerText()
                        )
                )
        );
    }

    @PostMapping("/sessions/{sessionId}/finish")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Завершить сессию", description = "Завершает сессию, запрашивает у LLM оценку ответов и формирует итоговый отчёт.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Отчёт сформирован"),
            @ApiResponse(responseCode = "403", description = "Сессия принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull SessionReport> finishSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var report = interviewService.finishSession(sessionId, userDetails.getId());
        return ResponseEntity
                .created(URI.create("/sessions/" + sessionId + "/report"))
                .body(report);
    }

    @GetMapping("/sessions/{sessionId}/report")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Получить отчёт по сессии", description = "Возвращает ранее сформированный отчёт по завершённой сессии.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отчёт найден"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull SessionReport> getReport(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                interviewService.getSessionReport(
                        sessionId, userDetails.getId()
                )
        );
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Удалить сессию", description = "Удаляет сессию собеседования вместе с вопросами.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Сессия удалена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> getSessionReport(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        interviewService.deleteSession(sessionId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
