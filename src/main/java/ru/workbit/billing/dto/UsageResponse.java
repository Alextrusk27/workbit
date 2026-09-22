package ru.workbit.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import ru.workbit.billing.model.UsageEvent;

public record UsageResponse(
        @Schema(description = "Остаток лимитов; просроченный баланс отдаётся нулём", example = "20")
        int limits,

        @Schema(description = "Срок действия лимитов (UTC); null, если лимитов нет")
        Instant expiresAt,

        @Schema(description = "Была ли хоть одна покупка пакета", example = "false")
        boolean paid,

        @Schema(description = "История операций, новые первыми")
        List<UsageEventResponse> events
) {
    public record UsageEventResponse(
            @Schema(description = "Момент операции (UTC)")
            Instant at,

            @Schema(description = "Вид операции: списание или зачисление", example = "SPEND")
            UsageEvent.Kind kind,

            @Schema(description = "Операция", example = "TRAINING")
            UsageEvent.Operation operation,

            @Schema(description = "Величина операции в лимитах, всегда положительная", example = "10")
            int delta,

            @Schema(description = "Описание операции для показа пользователю", example = "Тренировка — Java, Средний")
            String label
    ) {
    }
}
