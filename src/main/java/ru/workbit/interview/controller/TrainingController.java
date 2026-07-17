package ru.workbit.interview.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.workbit.exception.dto.ApiError;
import ru.workbit.interview.dto.*;
import ru.workbit.interview.service.TrainingService;
import ru.workbit.security.config.RateLimitProperties;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.RateLimiterService;
import ru.workbit.util.ClientIp;
import ru.workbit.util.annotation.Loggable;
import ru.workbit.util.annotation.Sensitive;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interview/training")
@Tag(name = "Training", description = "Тренировочное AI-собеседование: сессии, генерация вопросов, ответы, отчёт")
public class TrainingController {
    private final TrainingService trainingService;
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    @GetMapping("/options")
    @Loggable(logResult = true)
    @Operation(summary = "Справочник значений для создания тренировки", description = "Возвращает популярные профессии из словаря (подсказки для быстрого выбора, свободный ввод тоже допустим), допустимые уровни, а также лимит основных вопросов и минимум ответов для завершения тренировки.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Справочник значений")
    })
    public ResponseEntity<@NotNull TrainingOptionsResponse> getOptions() {
        return ResponseEntity.ok(trainingService.getOptions());
    }

    @GetMapping("/suggest/professions")
    @Loggable(logArgs = true)
    @Operation(summary = "Подсказки профессий", description = "Возвращает до 7 профессий из словаря по подстроке: сначала совпадения по началу названия, затем по популярности. Запрос короче 2 символов даёт пустой список.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список подсказок, возможно пустой"),
            @ApiResponse(responseCode = "429", description = "Превышен лимит запросов", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull List<String>> suggestProfessions(
            @RequestParam String query,
            HttpServletRequest httpRequest
    ) {
        rateLimiter.check("suggest:" + ClientIp.from(httpRequest), rateLimitProperties.suggest());
        return ResponseEntity.ok(trainingService.suggestProfessions(query));
    }

    @GetMapping("/suggest/topics")
    @Loggable(logArgs = true)
    @Operation(summary = "Подсказки тем", description = "Возвращает до 7 тем словаря для указанной профессии по подстроке: сначала совпадения по началу названия, затем по популярности. Неизвестная профессия или запрос короче 2 символов дают пустой список.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список подсказок, возможно пустой"),
            @ApiResponse(responseCode = "429", description = "Превышен лимит запросов", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull List<String>> suggestTopics(
            @RequestParam String profession,
            @RequestParam String query,
            HttpServletRequest httpRequest
    ) {
        rateLimiter.check("suggest:" + ClientIp.from(httpRequest), rateLimitProperties.suggest());
        return ResponseEntity.ok(trainingService.suggestTopics(profession, query));
    }

    @PostMapping("/normalize")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Распознавание введённых профессии и темы", description = "Проверяет свободный ввод через LLM: распознаваема ли профессия/тема, подходит ли тема профессии, и возвращает канонические варианты для подтверждения. Предназначен для случая, когда ввод не выбран из подсказок словаря; выбор предложенного варианта необязателен.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Результат распознавания"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Превышен лимит запросов", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull NormalizeInputResponse> normalizeInput(
            @RequestBody @Valid NormalizeInputRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimiter.check("normalize:" + ClientIp.from(httpRequest), rateLimitProperties.normalize());
        return ResponseEntity.ok(trainingService.normalizeInput(request));
    }

    @PostMapping("/sessions")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Создать тренировочную сессию", description = "Создаёт новую тренировочную сессию по указанной профессии (свободный ввод), необязательной теме и уровню. Вопросы заранее не генерируются: первый вопрос запрашивается отдельным вызовом.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сессия создана"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Профессия или тема не распознаны", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull TrainingSessionResponse> createSession(
            @RequestBody @Valid CreateSessionRequest request,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var session = trainingService.create(request, userDetails.getId());
        return ResponseEntity
                .created(URI.create("/sessions/" + session.id()))
                .body(session);
    }

    @GetMapping("/sessions")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Список тренировочных сессий пользователя", description = "Возвращает страницу тренировочных сессий текущего пользователя, по умолчанию новые первыми.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Страница сессий")
    })
    public ResponseEntity<@NotNull PagedModel<@NotNull TrainingSessionResponse>> getAllSessions(
            @PageableDefault(sort = "created", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(new PagedModel<>(trainingService.getAll(userDetails.getId(), pageable)));
    }

    @GetMapping("/sessions/{sessionId}")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Получить сессию по id", description = "Возвращает тренировочную сессию текущего пользователя.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сессия найдена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull TrainingSessionResponse> getSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(trainingService.get(sessionId, userDetails.getId()));
    }

    @PostMapping("/sessions/{sessionId}/questions/next")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Получить следующий вопрос", description = "Возвращает текущий неотвеченный вопрос сессии, а если его нет - генерирует следующий вопрос через LLM с учётом истории диалога. LLM может задать уточняющий вопрос к последнему ответу (followUp=true); такие вопросы не входят в счётчик основных. Вызов идемпотентен: повторный запрос возвращает тот же неотвеченный вопрос.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вопрос возвращён"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Достигнут лимит основных вопросов или сессия уже завершена", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull TrainingQuestionResponse> nextQuestion(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(trainingService.nextQuestion(sessionId, userDetails.getId()));
    }

    @PostMapping("/sessions/{sessionId}/questions/{questionId}")
    @Loggable(logArgs = true)
    @Operation(summary = "Отправить ответ на вопрос", description = "Сохраняет текст ответа на вопрос. Оценка по ходу тренировки не выдаётся: фидбэк формируется только при завершении.")
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
        trainingService.submitAnswer(
                new SubmitAnswerRequest(userDetails.getId(), sessionId, questionId, request.answerText()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{sessionId}/finish")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Завершить тренировку", description = "Завершает тренировку, запрашивает у LLM поразборный фидбэк по каждому ответу и формирует итоговый отчёт. Доступно после ответа минимум на 3 основных вопроса.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Отчёт сформирован"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Отвечено меньше 3 основных вопросов или сессия уже завершена", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull TrainingReportResponse> finishSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var report = trainingService.createReport(sessionId, userDetails.getId());
        return ResponseEntity
                .created(URI.create("/sessions/" + sessionId + "/report"))
                .body(report);
    }

    @GetMapping("/sessions/{sessionId}/report")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Получить отчёт по тренировке", description = "Возвращает ранее сформированный отчёт по завершённой тренировке, включая поразборный фидбэк по каждому вопросу.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отчёт найден"),
            @ApiResponse(responseCode = "404", description = "Сессия или отчёт не найдены", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull TrainingReportResponse> getReport(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(trainingService.getReport(sessionId, userDetails.getId()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Loggable(logArgs = true)
    @Operation(summary = "Удалить сессию", description = "Удаляет тренировочную сессию вместе с вопросами и отчётом.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Сессия удалена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> deleteSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        trainingService.delete(sessionId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
