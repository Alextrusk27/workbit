package ru.workbit.training.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.training.dto.TrainingQuestionResponse;
import ru.workbit.training.model.TrainingQuestion;

@Mapper(componentModel = "spring")
public interface TrainingQuestionMapper {

    @Mapping(target = "questionId", source = "id")
    @Mapping(target = "questionText", source = "text")
    @Mapping(target = "score", source = "feedback.score")
    @Mapping(target = "feedback", source = "feedback.text")
    TrainingQuestionResponse toDto(TrainingQuestion question);
}
