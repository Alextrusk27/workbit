package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.workbit.llm.agents.AnswerEvaluator;
import ru.workbit.interview.question.QuestionBank;
import ru.workbit.llm.dto.AnswerEvaluation;
import ru.workbit.llm.dto.AnswerEvaluationRequest;

@Service
@RequiredArgsConstructor
public class InterviewService {
    private final QuestionBank questionBank;
    private final AnswerEvaluator answerEvaluator;

    public AnswerEvaluation evaluateAnswer(AnswerEvaluationRequest request) {
        return answerEvaluator.evaluateAnswer(request);
    }
}
