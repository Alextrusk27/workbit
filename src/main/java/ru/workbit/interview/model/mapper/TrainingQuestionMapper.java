package ru.workbit.interview.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.interview.dto.TrainingQuestionResponse;
import ru.workbit.interview.model.TrainingQuestion;

@Mapper(componentModel = "spring")
public interface TrainingQuestionMapper {

    @Mapping(target = "questionId", source = "id")
    @Mapping(target = "score", source = "feedback.score")
    @Mapping(target = "feedback", source = "feedback.feedbackText")
    TrainingQuestionResponse toDto(TrainingQuestion question);
}
