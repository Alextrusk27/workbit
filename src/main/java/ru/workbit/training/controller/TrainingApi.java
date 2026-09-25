package ru.workbit.training.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.training.dto.CreateSessionRequest;
import ru.workbit.training.dto.FeedbackRequest;
import ru.workbit.training.dto.NormalizeInputRequest;
import ru.workbit.training.dto.NormalizeInputResponse;
import ru.workbit.training.dto.ReferenceAnswerResponse;
import ru.workbit.training.dto.SubmitAnswerBody;
import ru.workbit.training.dto.TrainingOptionsResponse;
import ru.workbit.training.dto.TrainingQuestionResponse;
import ru.workbit.training.dto.TrainingReportResponse;
import ru.workbit.training.dto.TrainingSessionResponse;

@RequestMapping("/api/v1/training")
@Tag(name = "Training", description = "Тренировочное AI-собеседование: сессии, генерация вопросов, ответы, отчёт")
public interface TrainingApi {

    @Operation(summary = "Справочник для тренировки")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Справочник значений")
    @GetMapping("/options")
    ResponseEntity<TrainingOptionsResponse> getOptions();

    @Operation(summary = "Подсказки профессий")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список подсказок"),
            @ApiResponse(responseCode = "429", description = "Слишком много запросов")
    })
    @GetMapping("/suggest/professions")
    ResponseEntity<List<String>> suggestProfessions(@RequestParam String query, HttpServletRequest httpRequest);

    @Operation(summary = "Подсказки навыков",
            description = "Ограничены выбранной профессией, если она указана.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список подсказок"),
            @ApiResponse(responseCode = "429", description = "Слишком много запросов")
    })
    @GetMapping("/suggest/skills")
    ResponseEntity<List<String>> suggestSkills(@RequestParam(required = false) @Nullable String profession,
                                               @RequestParam String query, HttpServletRequest httpRequest);

    @Operation(summary = "Распознавание навыка и профессии",
            description = "Проверяет через LLM ввод, не выбранный из подсказок словаря.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Результат распознавания"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос"),
            @ApiResponse(responseCode = "429", description = "Слишком много запросов"),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен")
    })
    @PostMapping("/normalize")
    ResponseEntity<NormalizeInputResponse> normalizeInput(@RequestBody @Valid NormalizeInputRequest request,
                                                           HttpServletRequest httpRequest);

    @Operation(summary = "Создать тренировочную сессию",
            description = "Создаёт сессию с вопросами по навыку, профессии и уровню сложности.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сессия создана"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос"),
            @ApiResponse(responseCode = "402", description = "Не хватает лимитов"),
            @ApiResponse(responseCode = "422", description = "Навык или профессия не распознаны")
    })
    @PostMapping("/sessions")
    ResponseEntity<TrainingSessionResponse> createSession(
            @RequestBody @Valid CreateSessionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Список сессий")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Страница сессий")
    @GetMapping("/sessions")
    ResponseEntity<PagedModel<TrainingSessionResponse>> getAllSessions(
            @PageableDefault(sort = "created", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Получить сессию")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сессия найдена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена")
    })
    @GetMapping("/sessions/{sessionId}")
    ResponseEntity<TrainingSessionResponse> getSession(
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
    ResponseEntity<List<TrainingQuestionResponse>> answeredQuestions(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Следующий вопрос",
            description = "Возвращает первый неотвеченный вопрос сессии. Идемпотентен.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вопрос возвращён"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена"),
            @ApiResponse(responseCode = "409", description = "Все вопросы сессии отвечены или сессия уже завершена")
    })
    @PostMapping("/sessions/{sessionId}/questions/next")
    ResponseEntity<TrainingQuestionResponse> nextQuestion(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Добавить ещё вопросы",
            description = "Добавляет в сессию следующую пачку вопросов.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вопросы добавлены"),
            @ApiResponse(responseCode = "402", description = "Не хватает лимитов"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена"),
            @ApiResponse(responseCode = "409",
                    description = "Сессия завершена, остались неотвеченные вопросы или новых вопросов больше нет"),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен")
    })
    @PostMapping("/sessions/{sessionId}/questions/more")
    ResponseEntity<TrainingSessionResponse> addQuestions(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Эталонный ответ на вопрос",
            description = "Первый просмотр платный и списывает лимит, повторный бесплатен.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Эталонный ответ"),
            @ApiResponse(responseCode = "402", description = "Не хватает лимитов"),
            @ApiResponse(responseCode = "403",
                    description = "Вопрос принадлежит другому пользователю или пакет ещё не покупался"),
            @ApiResponse(responseCode = "404", description = "Вопрос не найден"),
            @ApiResponse(responseCode = "409", description = "Вопрос не принадлежит указанной сессии"),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен")
    })
    @GetMapping("/sessions/{sessionId}/questions/{questionId}/reference-answer")
    ResponseEntity<ReferenceAnswerResponse> getReferenceAnswer(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Отправить ответ на вопрос",
            description = "Оценка появляется только при завершении тренировки, не сразу.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ответ сохранён"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос"),
            @ApiResponse(responseCode = "403", description = "Вопрос принадлежит другому пользователю"),
            @ApiResponse(responseCode = "404", description = "Вопрос не найден"),
            @ApiResponse(responseCode = "409",
                    description = "Вопрос уже отвечен, не принадлежит указанной сессии либо сессия завершена")
    })
    @PostMapping("/sessions/{sessionId}/questions/{questionId}")
    ResponseEntity<Void> submitAnswer(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @RequestBody @Valid SubmitAnswerBody request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Оценить разбор вопроса",
            description = "Отзыв анонимный, пользователю обратно не показывается.")
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

    @Operation(summary = "Завершить тренировку",
            description = "Формирует отчёт с фидбэком LLM по каждому ответу. Идемпотентен.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Отчёт сформирован"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена"),
            @ApiResponse(responseCode = "409", description = "Отвечено меньше 3 вопросов или сессия уже завершена"),
            @ApiResponse(responseCode = "503", description = "AI-сервис недоступен")
    })
    @PostMapping("/sessions/{sessionId}/finish")
    ResponseEntity<TrainingReportResponse> finishSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Отчёт по тренировке")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отчёт найден"),
            @ApiResponse(responseCode = "404", description = "Сессия или отчёт не найдены")
    })
    @GetMapping("/sessions/{sessionId}/report")
    ResponseEntity<TrainingReportResponse> getReport(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Пройти тренировку заново",
            description = "Вопросы остаются, ответы и отчёт стираются безвозвратно.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Тренировка перезапущена"),
            @ApiResponse(responseCode = "402", description = "Не хватает лимитов"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена"),
            @ApiResponse(responseCode = "409", description = "Тренировка ещё не завершена")
    })
    @PostMapping("/sessions/{sessionId}/restart")
    ResponseEntity<TrainingSessionResponse> restartSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Удалить сессию",
            description = "Удаляет тренировочную сессию вместе с вопросами и отчётом.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Сессия удалена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена")
    })
    @DeleteMapping("/sessions/{sessionId}")
    ResponseEntity<Void> deleteSession(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );
}
