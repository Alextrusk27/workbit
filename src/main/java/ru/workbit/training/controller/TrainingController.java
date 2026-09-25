package ru.workbit.training.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.workbit.security.config.RateLimitProperties;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.RateLimiterService;
import ru.workbit.training.dto.CreateSessionRequest;
import ru.workbit.training.dto.FeedbackRequest;
import ru.workbit.training.dto.NormalizeInputRequest;
import ru.workbit.training.dto.NormalizeInputResponse;
import ru.workbit.training.dto.ReferenceAnswerResponse;
import ru.workbit.training.dto.SubmitAnswerBody;
import ru.workbit.training.dto.SubmitAnswerRequest;
import ru.workbit.training.dto.TrainingOptionsResponse;
import ru.workbit.training.dto.TrainingQuestionResponse;
import ru.workbit.training.dto.TrainingReportResponse;
import ru.workbit.training.dto.TrainingSessionResponse;
import ru.workbit.training.service.TrainingService;
import ru.workbit.util.ClientIp;
import ru.workbit.util.annotation.Loggable;
import ru.workbit.util.annotation.Sensitive;

@RestController
@RequiredArgsConstructor
public class TrainingController implements TrainingApi {
    private final TrainingService trainingService;
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    @Override
    @Loggable(level = "DEBUG")
    public ResponseEntity<TrainingOptionsResponse> getOptions() {
        return ResponseEntity.ok(trainingService.getOptions());
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public ResponseEntity<List<String>> suggestProfessions(String query, HttpServletRequest httpRequest) {
        rateLimiter.check("suggest-professions:" + ClientIp.from(httpRequest), rateLimitProperties.suggest());
        return ResponseEntity.ok(trainingService.suggestProfessions(query));
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public ResponseEntity<List<String>> suggestSkills(@Nullable String profession, String query,
                                                       HttpServletRequest httpRequest) {
        rateLimiter.check("suggest-skills:" + ClientIp.from(httpRequest), rateLimitProperties.suggest());
        return ResponseEntity.ok(trainingService.suggestSkills(profession, query));
    }

    @Override
    @Loggable(logArgs = true, logResult = true)
    public ResponseEntity<NormalizeInputResponse> normalizeInput(NormalizeInputRequest request,
                                                                  HttpServletRequest httpRequest) {
        rateLimiter.check("normalize:" + ClientIp.from(httpRequest), rateLimitProperties.normalize());
        return ResponseEntity.ok(trainingService.normalizeInput(request));
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<TrainingSessionResponse> createSession(CreateSessionRequest request,
                                                                  @Sensitive CustomUserDetails userDetails) {
        var session = trainingService.create(request, userDetails.getId());
        return ResponseEntity
                .created(URI.create("/sessions/" + session.id()))
                .body(session);
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public ResponseEntity<PagedModel<TrainingSessionResponse>> getAllSessions(
            Pageable pageable, @Sensitive CustomUserDetails userDetails) {
        return ResponseEntity.ok(new PagedModel<>(trainingService.getAll(userDetails.getId(), pageable)));
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public ResponseEntity<TrainingSessionResponse> getSession(UUID sessionId,
                                                               @Sensitive CustomUserDetails userDetails) {
        return ResponseEntity.ok(trainingService.get(sessionId, userDetails.getId()));
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public ResponseEntity<List<TrainingQuestionResponse>> answeredQuestions(
            UUID sessionId, @Sensitive CustomUserDetails userDetails) {
        return ResponseEntity.ok(trainingService.getAnsweredQuestions(sessionId, userDetails.getId()));
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<TrainingQuestionResponse> nextQuestion(UUID sessionId,
                                                                  @Sensitive CustomUserDetails userDetails) {
        return ResponseEntity.ok(trainingService.nextQuestion(sessionId, userDetails.getId()));
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<TrainingSessionResponse> addQuestions(UUID sessionId,
                                                                 @Sensitive CustomUserDetails userDetails) {
        return ResponseEntity.ok(trainingService.addQuestions(sessionId, userDetails.getId()));
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<ReferenceAnswerResponse> getReferenceAnswer(UUID sessionId, UUID questionId,
                                                                       @Sensitive CustomUserDetails userDetails) {
        return ResponseEntity.ok(trainingService.getReferenceAnswer(sessionId, questionId, userDetails.getId()));
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<Void> submitAnswer(UUID sessionId, UUID questionId, @Sensitive SubmitAnswerBody request,
                                             @Sensitive CustomUserDetails userDetails) {
        trainingService.submitAnswer(
                new SubmitAnswerRequest(userDetails.getId(), sessionId, questionId, request.answerText()));
        return ResponseEntity.noContent().build();
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<Void> submitQuestionFeedback(UUID sessionId, UUID questionId,
                                                        @Sensitive FeedbackRequest request,
                                                        @Sensitive CustomUserDetails userDetails) {
        trainingService.submitQuestionFeedback(sessionId, questionId, userDetails.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<Void> submitReportFeedback(UUID sessionId, @Sensitive FeedbackRequest request,
                                                      @Sensitive CustomUserDetails userDetails) {
        trainingService.submitReportFeedback(sessionId, userDetails.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<TrainingReportResponse> finishSession(UUID sessionId,
                                                                 @Sensitive CustomUserDetails userDetails) {
        var report = trainingService.createReport(sessionId, userDetails.getId());
        return ResponseEntity
                .created(URI.create("/sessions/" + sessionId + "/report"))
                .body(report);
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public ResponseEntity<TrainingReportResponse> getReport(UUID sessionId, @Sensitive CustomUserDetails userDetails) {
        return ResponseEntity.ok(trainingService.getReport(sessionId, userDetails.getId()));
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<TrainingSessionResponse> restartSession(UUID sessionId,
                                                                   @Sensitive CustomUserDetails userDetails) {
        return ResponseEntity.ok(trainingService.restart(sessionId, userDetails.getId()));
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<Void> deleteSession(UUID sessionId, @Sensitive CustomUserDetails userDetails) {
        trainingService.delete(sessionId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
