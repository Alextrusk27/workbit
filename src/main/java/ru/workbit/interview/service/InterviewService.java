package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.exception.InternalServerException;
import ru.workbit.interview.dto.CreateSessionRequest;
import ru.workbit.interview.dto.SessionResponse;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.mapper.QuestionMapper;
import ru.workbit.interview.model.mapper.SessionMapper;
import ru.workbit.interview.question.BankQuestion;
import ru.workbit.interview.question.QuestionBank;
import ru.workbit.interview.repository.SessionRepository;
import ru.workbit.llm.agents.AnswerEvaluator;
import ru.workbit.llm.dto.AnswerEvaluation;
import ru.workbit.llm.dto.AnswerEvaluationRequest;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewService {
    private final QuestionBank questionBank;
    private final AnswerEvaluator answerEvaluator;

    private final SessionMapper sessionMapper;
    private final QuestionMapper questionMapper;

    private final SessionRepository sessionRepository;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request, UUID userId) {
        checkUserIsActive();

        InterviewSession session = sessionMapper.toEntity(request);
        session.setUserId(userId);
        session.setQuestions(createQuestions(request, session));
        session = sessionRepository.save(session);

        return new SessionResponse(session.getId());
    }

    private void checkUserIsActive() {}


    private List<InterviewQuestion> createQuestions(CreateSessionRequest request, InterviewSession session) {
        List<BankQuestion> questions = questionBank.forLevel(request.level(), request.totalQuestions());

        if (questions.isEmpty()) {
            log.error("Question storage did not return any questions. {}", request);
            throw new InternalServerException("No questions found");
        }

        return IntStream.range(0, questions.size())
                .mapToObj(i -> {
                    var question = questionMapper.toEntity(questions.get(i), session);
                    question.setOrderIndex(i + 1);
                    return question;
                })
                .toList();
    }

    public AnswerEvaluation evaluateAnswer(AnswerEvaluationRequest request) {
        return answerEvaluator.evaluateAnswer(request);
    }
}
