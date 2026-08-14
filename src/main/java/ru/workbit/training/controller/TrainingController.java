package ru.workbit.training.controller;

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
import ru.workbit.training.dto.*;
import ru.workbit.training.service.TrainingService;
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
@RequestMapping("/api/v1/training")
@Tag(name = "Training", description = "Тренировочное AI-собеседование: сессии, генерация вопросов, ответы, отчёт")
public class TrainingController {
    private final TrainingService trainingService;
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    @GetMapping("/options")
    @Loggable(logResult = true)
    @Operation(summary = "Справочник значений для создания тренировки", description = "Возвращает популярные навыки и профессии из словаря (подсказки для быстрого выбора, свободный ввод тоже допустим), уровни сложности, а также лимит вопросов и минимум ответов для завершения тренировки.")
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
        rateLimiter.check("suggest-professions:" + ClientIp.from(httpRequest), rateLimitProperties.suggest());
        return ResponseEntity.ok(trainingService.suggestProfessions(query));
    }

    @GetMapping("/suggest/skills")
    @Loggable(logArgs = true)
    @Operation(summary = "Подсказки навыков", description = "Возвращает до 7 навыков словаря по подстроке: сначала совпадения по началу названия, затем по популярности. Если передана профессия, подсказки ограничены её навыками, иначе собираются по всему словарю. Запрос короче 2 символов даёт пустой список.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список подсказок, возможно пустой"),
            @ApiResponse(responseCode = "429", description = "Превышен лимит запросов", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull List<String>> suggestSkills(
            @RequestParam(required = false) String profession,
            @RequestParam String query,
            HttpServletRequest httpRequest
    ) {
        rateLimiter.check("suggest-skills:" + ClientIp.from(httpRequest), rateLimitProperties.suggest());
        return ResponseEntity.ok(trainingService.suggestSkills(profession, query));
    }

    @PostMapping("/normalize")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Распознавание введённых навыка и профессии", description = "Проверяет свободный ввод через LLM: распознаваемы ли навык и профессия, подходит ли навык профессии, и возвращает канонические варианты для подтверждения. Предназначен для случая, когда ввод не выбран из подсказок словаря; выбор предложенного варианта необязателен.")
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
    @Operation(summary = "Создать тренировочную сессию", description = "Создаёт новую тренировочную сессию по указанному навыку и профессии (свободный ввод) и уровню сложности. Вопросы отбираются из банка и при нехватке добираются через LLM сразу при создании.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сессия создана"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Навык или профессия не распознаны", content = @Content(schema = @Schema(implementation = ApiError.class)))
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
    @Operation(summary = "Получить следующий вопрос", description = "Возвращает первый неотвеченный вопрос сессии. Вызов идемпотентен: повторный запрос возвращает тот же вопрос, пока на него не ответили.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вопрос возвращён"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Все вопросы сессии отвечены или сессия уже завершена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull TrainingQuestionResponse> nextQuestion(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(trainingService.nextQuestion(sessionId, userDetails.getId()));
    }

    @PostMapping("/sessions/{sessionId}/questions/more")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Добавить ещё пачку вопросов", description = "Добавляет в незавершённую сессию следующие 10 вопросов - альтернатива разбору, когда все вопросы уже отвечены. Вопросы новые: банк отдаёт только не виденное пользователем, недостающее генерирует LLM с оглядкой на уже заданные. Всего в тренировке не больше 50 вопросов.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вопросы добавлены"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Сессия завершена, остались неотвеченные вопросы, достигнут потолок в 50 вопросов или новых вопросов этого уровня больше нет", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull TrainingSessionResponse> addQuestions(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(trainingService.addQuestions(sessionId, userDetails.getId()));
    }

    @GetMapping("/sessions/{sessionId}/questions/{questionId}/reference-answer")
    @Loggable(logArgs = true)
    @Operation(summary = "Посмотреть эталонный ответ", description = "Возвращает эталонный ответ на вопрос: у вопроса из банка он подготовлен заранее, у сгенерированного - создаётся через LLM при первом запросе и далее отдаётся из кеша.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Эталонный ответ"),
            @ApiResponse(responseCode = "403", description = "Вопрос принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Вопрос не найден", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Вопрос не принадлежит указанной сессии", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull ReferenceAnswerResponse> getReferenceAnswer(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(trainingService.getReferenceAnswer(sessionId, questionId, userDetails.getId()));
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

    @PostMapping("/sessions/{sessionId}/questions/{questionId}/feedback")
    @Loggable(logArgs = true)
    @Operation(summary = "Оценить разбор вопроса", description = "Сохраняет отзыв пользователя на разбор вопроса: лайк или дизлайк, у дизлайка — причины и необязательный комментарий. Отзыв анонимно помогает улучшать вопросы и разборы, пользователю обратно не показывается.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Отзыв сохранён"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Вопрос принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Вопрос не найден", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Вопрос не принадлежит указанной сессии", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> submitQuestionFeedback(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @RequestBody @Valid @Sensitive FeedbackRequest request,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        trainingService.submitQuestionFeedback(sessionId, questionId, userDetails.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{sessionId}/report/feedback")
    @Loggable(logArgs = true)
    @Operation(summary = "Оценить итоговый отчёт", description = "Сохраняет отзыв пользователя на итоговый отчёт тренировки: лайк или дизлайк, у дизлайка — причины и необязательный комментарий.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Отзыв сохранён"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Сессия или отчёт не найдены", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> submitReportFeedback(
            @PathVariable UUID sessionId,
            @RequestBody @Valid @Sensitive FeedbackRequest request,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        trainingService.submitReportFeedback(sessionId, userDetails.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{sessionId}/finish")
    @Loggable(logArgs = true)
    @Operation(summary = "Завершить тренировку", description = "Завершает тренировку, запрашивает у LLM поразборный фидбэк по каждому ответу и формирует итоговый отчёт. Доступно после ответа минимум на 3 вопроса.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Отчёт сформирован"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Отвечено меньше 3 вопросов или сессия уже завершена", content = @Content(schema = @Schema(implementation = ApiError.class))),
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
    @Loggable(logArgs = true)
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

    @PostMapping("/sessions/{sessionId}/restart")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Пройти тренировку заново", description = "Возвращает завершённую тренировку в исходное состояние: вопросы и эталонные ответы остаются те же, ответы, фидбэк и отчёт стираются безвозвратно.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Тренировка перезапущена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Тренировка ещё не завершена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull TrainingSessionResponse> restartSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(trainingService.restart(sessionId, userDetails.getId()));
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
