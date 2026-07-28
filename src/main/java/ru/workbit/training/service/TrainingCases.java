package ru.workbit.training.service;

import lombok.extern.slf4j.Slf4j;
import ru.workbit.exception.ConflictException;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
final class TrainingCases {

    private TrainingCases() {
    }

    static List<TrainingQuestion> answeredSorted(TrainingSession session) {
        return session.getQuestions().stream()
                .filter(TrainingQuestion::isAnswered)
                .sorted(Comparator.comparingInt(TrainingQuestion::getOrderIndex))
                .toList();
    }

    static List<List<TrainingQuestion>> groupCases(List<TrainingQuestion> answered) {
        Map<UUID, List<TrainingQuestion>> followUpsByParent = answered.stream()
                .filter(TrainingQuestion::isFollowUp)
                .collect(Collectors.groupingBy(TrainingQuestion::getParentQuestionId));

        return answered.stream()
                .filter(q -> !q.isFollowUp())
                .sorted(Comparator.comparingInt(TrainingQuestion::getOrderIndex))
                .map(main -> {
                    List<TrainingQuestion> trainingCase = new ArrayList<>();
                    trainingCase.add(main);
                    followUpsByParent.getOrDefault(main.getId(), List.of()).stream()
                            .sorted(Comparator.comparingInt(TrainingQuestion::getOrderIndex))
                            .forEach(trainingCase::add);
                    return trainingCase;
                })
                .toList();
    }

    static void checkSessionNotCompleted(TrainingSession session) {
        if (session.getStatus() == TrainingSession.Status.COMPLETED) {
            log.warn("Session {} is already completed", session.getId());
            throw new ConflictException("Session already finished");
        }
    }
}
