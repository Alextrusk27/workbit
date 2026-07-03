package ru.workbit.interview.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

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
}
