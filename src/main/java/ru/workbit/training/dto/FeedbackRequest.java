package ru.workbit.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.workbit.training.model.TrainingUserFeedback;

import java.util.List;

public record FeedbackRequest(
        @Schema(description = "Оценка разбора", example = "DOWN")
        @NotNull
        TrainingUserFeedback.Vote vote,

        @Schema(description = "Выбранные причины дизлайка; у лайка пустой список", example = "[\"Оценка занижена\"]")
        @NotNull
        @Size(max = 10)
        List<@Size(max = 100) String> reasons,

        @Schema(description = "Свободный комментарий", example = "Разбор не заметил вторую часть ответа")
        @Size(max = 2000)
        String comment
) {
}
