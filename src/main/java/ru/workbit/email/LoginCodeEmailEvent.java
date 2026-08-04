package ru.workbit.email;

public record LoginCodeEmailEvent(String email, String code) {
}
