package ru.workbit.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.billing.model.UsageEvent;

import java.time.Instant;
import java.util.List;

public record UsageResponse(
        @Schema(description = "Счётчик интервью текущего тарифа")
        UsageCounter interviews,

        @Schema(description = "Счётчик тренировок текущего тарифа")
        UsageCounter trainings,

        @Schema(description = "История операций, новые первыми")
        List<UsageEventResponse> events
) {
    public record UsageCounter(
            @Schema(description = "Остаток; null — безлимит", example = "4")
            Integer left,

            @Schema(description = "Выдано всего; null — безлимит", example = "10")
            Integer total
    ) {
    }

    public record UsageEventResponse(
            @Schema(description = "Момент операции (UTC)")
            Instant at,

            @Schema(description = "Вид операции", example = "SPEND")
            UsageEvent.Kind kind,

            @Schema(description = "Что списано или зачислено", example = "TRAINING")
            UsageEvent.Target target,

            @Schema(description = "Величина операции, всегда положительная", example = "1")
            int delta,

            @Schema(description = "Описание операции для показа пользователю", example = "Тренировка — Java, Уверенный")
            String label
    ) {
    }
}
