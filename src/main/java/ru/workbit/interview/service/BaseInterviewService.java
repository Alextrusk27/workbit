package ru.workbit.interview.service;

import ru.workbit.interview.dto.QuestionResponse;
import ru.workbit.interview.dto.ReportResponse;
import ru.workbit.interview.dto.SessionResponse;

public abstract class BaseInterviewService<S extends SessionResponse, Q extends QuestionResponse,
        R extends ReportResponse> implements InterviewService<S, Q, R> {
}
