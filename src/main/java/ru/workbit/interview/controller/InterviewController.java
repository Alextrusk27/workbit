package ru.workbit.interview.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.workbit.interview.dto.CreateInterviewSessionRequest;
import ru.workbit.interview.dto.FeedbackRequest;
import ru.workbit.interview.dto.InterviewQuestionResponse;
import ru.workbit.interview.dto.InterviewReportResponse;
import ru.workbit.interview.dto.InterviewSessionResponse;
import ru.workbit.interview.dto.InterviewVacancyDetailResponse;
import ru.workbit.interview.dto.InterviewVacancyResponse;
import ru.workbit.interview.dto.SubmitAnswerBody;
import ru.workbit.interview.dto.SubmitAnswerRequest;
import ru.workbit.interview.service.InterviewService;
import ru.workbit.interview.service.InterviewVacancyService;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.util.annotation.Loggable;
import ru.workbit.util.annotation.Sensitive;

@RestController
@RequiredArgsConstructor
public class InterviewController implements InterviewApi {
    private final InterviewService interviewService;
    private final InterviewVacancyService interviewVacancyService;

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<InterviewSessionResponse> createSession(
            CreateInterviewSessionRequest request,
            @Sensitive CustomUserDetails userDetails
    ) {
        var session = interviewService.createSession(request.vacancyUrl(), userDetails.getId());
        return ResponseEntity
                .created(URI.create("/sessions/" + session.id()))
                .body(session);
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public ResponseEntity<InterviewSessionResponse> getSession(
            UUID sessionId,
            @Sensitive CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(interviewService.get(sessionId, userDetails.getId()));
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public ResponseEntity<List<InterviewQuestionResponse>> answeredQuestions(
            UUID sessionId,
            @Sensitive CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(interviewService.getAnsweredQuestions(sessionId, userDetails.getId()));
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<InterviewQuestionResponse> nextQuestion(
            UUID sessionId,
            @Sensitive CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(interviewService.nextQuestion(sessionId, userDetails.getId()));
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<Void> submitAnswer(
            UUID sessionId,
            UUID questionId,
            @Sensitive SubmitAnswerBody request,
            @Sensitive CustomUserDetails userDetails
    ) {
        interviewService.submitAnswer(
                new SubmitAnswerRequest(userDetails.getId(), sessionId, questionId, request.answerText()));
        return ResponseEntity.noContent().build();
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<Void> submitQuestionFeedback(
            UUID sessionId,
            UUID questionId,
            @Sensitive FeedbackRequest request,
            @Sensitive CustomUserDetails userDetails
    ) {
        interviewService.submitQuestionFeedback(sessionId, questionId, userDetails.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<Void> submitReportFeedback(
            UUID sessionId,
            @Sensitive FeedbackRequest request,
            @Sensitive CustomUserDetails userDetails
    ) {
        interviewService.submitReportFeedback(sessionId, userDetails.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<InterviewReportResponse> finishSession(
            UUID sessionId,
            @Sensitive CustomUserDetails userDetails
    ) {
        var report = interviewService.createReport(sessionId, userDetails.getId());
        return ResponseEntity
                .created(URI.create("/sessions/" + sessionId + "/report"))
                .body(report);
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public ResponseEntity<InterviewReportResponse> getReport(UUID sessionId, @Sensitive CustomUserDetails userDetails) {
        return ResponseEntity.ok(interviewService.getReport(sessionId, userDetails.getId()));
    }

    @Override
    @Loggable(level = "DEBUG")
    public ResponseEntity<List<InterviewVacancyResponse>> getAllVacancies(@Sensitive CustomUserDetails userDetails) {
        return ResponseEntity.ok(interviewVacancyService.getAll(userDetails.getId()));
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public ResponseEntity<InterviewVacancyDetailResponse> getVacancy(
            String vacancyId,
            @Sensitive CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(interviewVacancyService.get(vacancyId, userDetails.getId()));
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<Void> deleteVacancy(String vacancyId, @Sensitive CustomUserDetails userDetails) {
        interviewVacancyService.delete(vacancyId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
