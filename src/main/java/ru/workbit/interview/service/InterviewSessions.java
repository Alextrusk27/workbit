package ru.workbit.interview.service;

import lombok.extern.slf4j.Slf4j;
import ru.workbit.exception.ConflictException;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;

import java.util.Comparator;
import java.util.List;

@Slf4j
final class InterviewSessions {

    private InterviewSessions() {
    }

    static List<InterviewQuestion> answeredSorted(InterviewSession session) {
        return session.getQuestions().stream()
                .filter(InterviewQuestion::isAnswered)
                .sorted(Comparator.comparingInt(InterviewQuestion::getOrderIndex))
                .toList();
    }

    static void checkSessionNotCompleted(InterviewSession session) {
        if (session.getStatus() == InterviewSession.Status.COMPLETED) {
            log.warn("Interview session {} is already completed", session.getId());
            throw new ConflictException("Session already finished");
        }
    }
}
