package ru.workbit.interview.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.interview.dto.TrainingReportResponse;
import ru.workbit.interview.model.TrainingQuestion;
import ru.workbit.interview.model.TrainingReport;
import ru.workbit.interview.model.TrainingSession;

import java.util.List;

@Mapper(componentModel = "spring", uses = TrainingQuestionMapper.class)
public interface TrainingReportMapper {

    @Mapping(target = "reportId", source = "report.id")
    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "profession", source = "session.profession")
    @Mapping(target = "level", source = "session.level")
    @Mapping(target = "avgScore", source = "report.avgScore")
    @Mapping(target = "overallFeedback", source = "report.overallFeedback")
    @Mapping(target = "generatedAt", source = "report.generatedAt")
    TrainingReportResponse toResponse(TrainingReport report, TrainingSession session,
                                      List<TrainingQuestion> questions);
}
