package ru.workbit.interview.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.interview.dto.CreateSessionRequest;
import ru.workbit.interview.model.InterviewSession;

@Mapper(componentModel = "spring")
public interface SessionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "questions", ignore = true)
    @Mapping(target = "status", ignore = true)
    InterviewSession toEntity(CreateSessionRequest request);
}
