package ru.workbit.interview.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import ru.workbit.interview.dto.CreateSessionRequest;
import ru.workbit.interview.dto.SessionReport;
import ru.workbit.interview.dto.SessionResponse;
import ru.workbit.interview.model.InterviewSession;

@Mapper(componentModel = "spring")
public interface SessionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "questions", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "interviewReport", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "vacancySnapshotId", ignore = true)
    InterviewSession toEntity(CreateSessionRequest request);

    @Mapping(target = "answeredCount", source = "answeredCount")
    SessionResponse toResponse(InterviewSession session, int answeredCount);

    @Mappings({
            @Mapping(target = "reportId", source = "session.interviewReport.id"),
            @Mapping(target = "sessionId", source = "session.id"),
            @Mapping(target = "avgScore", source = "session.interviewReport.avgScore"),
            @Mapping(target = "overallFeedback", source = "session.interviewReport.overallFeedback"),
            @Mapping(target = "offerProbability", source = "session.interviewReport.offerProbability"),
            @Mapping(target = "generatedAt", source = "session.interviewReport.generatedAt")
    })
    SessionReport toSessionReport(InterviewSession session);
}
