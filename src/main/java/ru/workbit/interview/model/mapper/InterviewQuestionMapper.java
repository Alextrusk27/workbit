package ru.workbit.interview.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.interview.dto.InterviewQuestionResponse;
import ru.workbit.interview.model.InterviewQuestion;

@Mapper(componentModel = "spring")
public interface InterviewQuestionMapper {

    @Mapping(target = "questionId", source = "id")
    @Mapping(target = "questionText", source = "text")
    @Mapping(target = "score", source = "feedback.score")
    @Mapping(target = "feedback", source = "feedback.text")
    InterviewQuestionResponse toDto(InterviewQuestion question);
}
