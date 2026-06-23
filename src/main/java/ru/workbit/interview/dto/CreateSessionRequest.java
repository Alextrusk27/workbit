package ru.workbit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jetbrains.annotations.NotNull;
import ru.workbit.interview.model.CompanyType;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.Profession;

public record CreateSessionRequest(
        @NotNull
        Profession profession,

        @NotNull
        Level level,

        @NotNull
        CompanyType companyType,

        @NotNull
        @Min(10) @Max(20)
        Integer totalQuestions
) {
}