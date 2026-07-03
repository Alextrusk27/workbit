package ru.workbit.interview.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.interview.dto.QuestionResponse;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.question.BankQuestion;

@Mapper(componentModel = "spring")
public interface QuestionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderIndex", ignore = true)
    @Mapping(target = "feedback", ignore = true)
    @Mapping(target = "answered", ignore = true)
    @Mapping(target = "answerText", ignore = true)
    @Mapping(target = "answeredAt", ignore = true)
    @Mapping(target = "session", source = "session")
    @Mapping(target = "questionText", source = "question.text")
    InterviewQuestion toEntity(BankQuestion question, InterviewSession session);

    @Mapping(target = "questionId", source = "question.id")
    @Mapping(target = "feedback", source = "question.feedback.feedbackText")
    @Mapping(target = "score", source = "question.feedback.score")
    QuestionResponse toDto(InterviewQuestion question);
}
