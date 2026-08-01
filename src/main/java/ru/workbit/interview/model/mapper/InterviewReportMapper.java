package ru.workbit.interview.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.interview.dto.InterviewReportResponse;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;

import java.util.List;

@Mapper(componentModel = "spring", uses = InterviewQuestionMapper.class)
public interface InterviewReportMapper {

    @Mapping(target = "reportId", source = "report.id")
    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "avgScore", source = "report.avgScore")
    @Mapping(target = "offerProbability", source = "report.offerProbability")
    @Mapping(target = "overallFeedback", source = "report.overallFeedback")
    @Mapping(target = "recommendations", source = "report.recommendations")
    @Mapping(target = "weakestSkill", source = "report.weakestSkill")
    @Mapping(target = "generatedAt", source = "report.generatedAt")
    @Mapping(target = "questions", source = "questions")
    InterviewReportResponse toResponse(InterviewReport report, InterviewSession session,
                                       List<InterviewQuestion> questions);
}
