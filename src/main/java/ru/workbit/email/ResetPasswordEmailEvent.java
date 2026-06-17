package ru.workbit.email;

public record ResetPasswordEmailEvent(String email, String token) {
}
