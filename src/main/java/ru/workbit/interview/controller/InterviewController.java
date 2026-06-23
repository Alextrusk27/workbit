package ru.workbit.interview.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.workbit.interview.service.InterviewService;
import ru.workbit.llm.dto.AnswerEvaluation;
import ru.workbit.llm.dto.AnswerEvaluationRequest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interview")
public class InterviewController {
    private final InterviewService interviewService;



    @GetMapping("/test")
    public AnswerEvaluation test(@RequestBody AnswerEvaluationRequest request) {
        return interviewService.evaluateAnswer(request);
    }
}
