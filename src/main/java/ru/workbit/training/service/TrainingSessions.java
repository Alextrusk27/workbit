package ru.workbit.training.service;

import lombok.extern.slf4j.Slf4j;
import ru.workbit.exception.ConflictException;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingSession;

import java.util.Comparator;
import java.util.List;

@Slf4j
final class TrainingSessions {

    private TrainingSessions() {
    }

    static List<TrainingQuestion> answeredSorted(TrainingSession session) {
        return session.getQuestions().stream()
                .filter(TrainingQuestion::isAnswered)
                .sorted(Comparator.comparingInt(TrainingQuestion::getOrderIndex))
                .toList();
    }

    static void checkSessionNotCompleted(TrainingSession session) {
        if (session.getStatus() == TrainingSession.Status.COMPLETED) {
            log.warn("Session {} is already completed", session.getId());
            throw new ConflictException("Session already finished");
        }
    }

    static void checkSessionCompleted(TrainingSession session) {
        if (session.getStatus() != TrainingSession.Status.COMPLETED) {
            log.warn("Session {} is not finished yet", session.getId());
            throw new ConflictException("Session is not finished");
        }
    }
}
