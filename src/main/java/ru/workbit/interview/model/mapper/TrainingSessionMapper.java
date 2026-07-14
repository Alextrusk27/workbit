package ru.workbit.interview.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.interview.dto.CreateSessionRequest;
import ru.workbit.interview.dto.TrainingSessionResponse;
import ru.workbit.interview.model.TrainingSession;

@Mapper(componentModel = "spring")
public interface TrainingSessionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "questions", ignore = true)
    @Mapping(target = "report", ignore = true)
    TrainingSession toEntity(CreateSessionRequest request);

    @Mapping(target = "answeredCount", source = "answeredCount")
    TrainingSessionResponse toResponse(TrainingSession session, int answeredCount);
}
