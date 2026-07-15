package ru.workbit.auth;

import java.util.UUID;

public record UserReactivatedEvent(UUID userId) {
}
