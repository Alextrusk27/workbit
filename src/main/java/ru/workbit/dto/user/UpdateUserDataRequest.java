package ru.workbit.dto.user;

import jakarta.validation.constraints.Size;

public record UpdateUserDataRequest(

        @Size(max = 100)
        String lastName,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String middleName
) {
}
