package ru.workbit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;

public record LoginRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 8)
        String password
) {
    @Override
    @NotNull
    public String toString() {
        return "LoginRequest{email=%s, password=*****}".formatted(email);
    }
}
