package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.interview.model.Category;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.SessionSource;
import ru.workbit.interview.repository.SessionRepository;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.service.VacancyService;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
class VacancySessionCreator {
    private final VacancyService vacancyService;
    private final SessionRepository sessionRepository;

    @Transactional
    public InterviewSession persist(VacancyData data, String name, List<String> questionTexts, UUID userId) {
        UUID snapshotId = vacancyService.saveSnapshot(data, name);

        InterviewSession session = InterviewSession.builder()
                .userId(userId)
                .source(SessionSource.VACANCY)
                .vacancySnapshotId(snapshotId)
                .totalQuestions(questionTexts.size())
                .build();

        List<InterviewQuestion> questions = IntStream.range(0, questionTexts.size())
                .mapToObj(i -> InterviewQuestion.builder()
                        .session(session)
                        .category(Category.VACANCY)
                        .questionText(questionTexts.get(i))
                        .orderIndex(i + 1)
                        .build())
                .toList();
        session.setQuestions(questions);

        return sessionRepository.save(session);
    }
}
