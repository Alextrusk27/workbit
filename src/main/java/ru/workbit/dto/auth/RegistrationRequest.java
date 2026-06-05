package ru.workbit.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;

public record RegistrationRequest(
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
        return "RegisterRequest{email=%s, password=*****}";
    }
}
