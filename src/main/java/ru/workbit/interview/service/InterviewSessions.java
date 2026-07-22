package ru.workbit.interview.service;

import lombok.extern.slf4j.Slf4j;
import ru.workbit.exception.ConflictException;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    static List<List<InterviewQuestion>> groupCases(List<InterviewQuestion> answered) {
        Map<UUID, List<InterviewQuestion>> followUpsByParent = answered.stream()
                .filter(InterviewQuestion::isFollowUp)
                .collect(Collectors.groupingBy(InterviewQuestion::getParentQuestionId));

        return answered.stream()
                .filter(q -> !q.isFollowUp())
                .sorted(Comparator.comparingInt(InterviewQuestion::getOrderIndex))
                .map(main -> {
                    List<InterviewQuestion> interviewCase = new ArrayList<>();
                    interviewCase.add(main);
                    followUpsByParent.getOrDefault(main.getId(), List.of()).stream()
                            .sorted(Comparator.comparingInt(InterviewQuestion::getOrderIndex))
                            .forEach(interviewCase::add);
                    return interviewCase;
                })
                .toList();
    }

    static void checkSessionNotCompleted(InterviewSession session) {
        if (session.getStatus() == InterviewSession.Status.COMPLETED) {
            log.warn("Interview session {} is already completed", session.getId());
            throw new ConflictException("Session already finished");
        }
    }
}
