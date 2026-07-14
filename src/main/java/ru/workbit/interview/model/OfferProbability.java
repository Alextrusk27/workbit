package ru.workbit.interview.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Optional;

@Getter
public enum OfferProbability {
    LOW("Низкая"),
    MEDIUM("Средняя"),
    HIGH("Высокая");

    @JsonValue
    private final String name;

    OfferProbability(String name) {
       this.name = name;
    }

    public static Optional<OfferProbability> fromString(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        for (OfferProbability probability : values()) {
            if (probability.name().equalsIgnoreCase(normalized)
                    || probability.name.equalsIgnoreCase(normalized)) {
                return Optional.of(probability);
            }
        }
        return Optional.empty();
    }
}
