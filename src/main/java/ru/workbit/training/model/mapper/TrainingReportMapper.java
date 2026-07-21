package ru.workbit.training.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.training.dto.TrainingReportResponse;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingReport;
import ru.workbit.training.model.TrainingSession;

import java.util.List;

@Mapper(componentModel = "spring", uses = TrainingQuestionMapper.class)
public interface TrainingReportMapper {

    @Mapping(target = "reportId", source = "report.id")
    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "profession", source = "session.profession")
    @Mapping(target = "topic", source = "session.topic")
    @Mapping(target = "level", source = "session.level")
    @Mapping(target = "avgScore", source = "report.avgScore")
    @Mapping(target = "overallFeedback", source = "report.overallFeedback")
    @Mapping(target = "generatedAt", source = "report.generatedAt")
    TrainingReportResponse toResponse(TrainingReport report, TrainingSession session,
                                      List<TrainingQuestion> questions);
}
