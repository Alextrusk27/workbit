package ru.workbit.auth;

import java.util.List;
import java.util.UUID;

public record UsersDeletedEvent(List<UUID> userIds) {
}
