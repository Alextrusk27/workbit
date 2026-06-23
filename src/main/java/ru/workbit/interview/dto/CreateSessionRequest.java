package ru.workbit.interview.dto;

import org.jetbrains.annotations.NotNull;
import ru.workbit.interview.model.CompanyType;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.Profession;

public record CreateSessionRequest(
        @NotNull Profession profession,
        @NotNull Level level,
        @NotNull CompanyType company
) {
}
