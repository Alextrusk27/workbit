package ru.workbit.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.billing.model.UsageEvent;

import java.time.Instant;
import java.util.List;

public record UsageResponse(
        @Schema(description = "Счётчики интервью")
        UsageCounters interviews,

        @Schema(description = "Счётчики тренировок")
        UsageCounters trainings,

        @Schema(description = "История операций, новые первыми")
        List<UsageEventResponse> events
) {
    public record UsageCounters(
            @Schema(description = "Подписочная квота текущего тарифа")
            UsageCounter plan,

            @Schema(description = "Квота из докупленных пакетов")
            UsageCounter pack
    ) {
    }

    public record UsageCounter(
            @Schema(description = "Остаток", example = "4")
            int left,

            @Schema(description = "Выдано всего", example = "10")
            int total
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
