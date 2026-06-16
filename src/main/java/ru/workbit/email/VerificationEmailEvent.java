package ru.workbit.email;

public record VerificationEmailEvent(String email, String token) {
}
