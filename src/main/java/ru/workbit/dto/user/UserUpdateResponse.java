package ru.workbit.dto.user;

import java.util.UUID;

public record UserUpdateResponse(
        UUID id,
        String email
) {
}
