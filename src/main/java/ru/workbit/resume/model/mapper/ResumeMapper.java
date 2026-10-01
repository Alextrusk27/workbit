package ru.workbit.resume.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.resume.dto.ResumeResponse;
import ru.workbit.resume.model.Resume;

@Mapper(componentModel = "spring")
public interface ResumeMapper {

    @Mapping(target = "uploadedAt", source = "createdAt")
    ResumeResponse toResponse(Resume resume);
}
