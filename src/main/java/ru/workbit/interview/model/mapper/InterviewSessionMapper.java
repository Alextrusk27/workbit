package ru.workbit.interview.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.interview.dto.InterviewSessionResponse;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancySnapshotView;

@Mapper(componentModel = "spring")
public interface InterviewSessionMapper {

    @Mapping(target = "vacancyName", source = "vacancyData.name")
    @Mapping(target = "employer", source = "vacancyData.employer")
    @Mapping(target = "answeredCount", source = "answeredCount")
    InterviewSessionResponse toResponse(InterviewSession interviewSession, VacancyData vacancyData, int answeredCount);

    @Mapping(target = "vacancyName", source = "vacancy.name")
    @Mapping(target = "employer", source = "vacancy.employer")
    @Mapping(target = "answeredCount", source = "answeredCount")
    InterviewSessionResponse toResponse(InterviewSession interviewSession, VacancySnapshotView vacancy, int answeredCount);
}
