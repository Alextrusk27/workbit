package ru.workbit.interview.service;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.workbit.interview.dto.*;

import java.util.UUID;

public interface InterviewService<S extends SessionResponse, Q extends QuestionResponse,
        R extends ReportResponse> {

    S create(CreateSessionRequest request, UUID userId);

    S get(UUID sessionId, UUID userId);

    Page<@NotNull S> getAll(UUID userId, Pageable pageable);

    Q nextQuestion(UUID sessionId, UUID userId);

    void submitAnswer(SubmitAnswerRequest request);

    R createReport(UUID sessionId, UUID userId);

    R getReport(UUID sessionId, UUID userId);

    void delete(UUID sessionId, UUID userId);
}
