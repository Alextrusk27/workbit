package ru.workbit.interview.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.workbit.interview.dto.CreateSessionRequest;
import ru.workbit.interview.dto.SessionResponse;
import ru.workbit.interview.service.InterviewService;
import ru.workbit.llm.dto.AnswerEvaluation;
import ru.workbit.llm.dto.AnswerEvaluationRequest;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.util.annotation.Loggable;
import ru.workbit.util.annotation.Sensitive;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interview")
public class InterviewController {
    private final InterviewService interviewService;

    @PostMapping("/sessions")
    @Loggable(logArgs = true, logResult = true)
    public ResponseEntity<@NotNull SessionResponse> createSession(
            @RequestBody @Valid CreateSessionRequest request,
            @Sensitive @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok().body(
                interviewService.createSession(request, userDetails.getId())
        );
    }

    @GetMapping("/test")
    public AnswerEvaluation test(@RequestBody AnswerEvaluationRequest request) {
        return interviewService.evaluateAnswer(request);
    }
}
