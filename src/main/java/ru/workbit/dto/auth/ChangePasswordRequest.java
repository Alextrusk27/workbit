package ru.workbit.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;

public record ChangePasswordRequest(
        @NotBlank
        String oldPassword,

        @NotBlank
        @Size(min = 8)
        String newPassword
) {
    @Override
    public @NotNull String toString() {
        return "ChangePasswordRequest{oldPassword=*****, newPassword=*****}";
    }
}
