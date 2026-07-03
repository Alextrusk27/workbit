package ru.workbit.interview.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum Level {
    JUNIOR("Junior"),
    MIDDLE("Middle"),
    SENIOR("Senior"),
    LEAD("Lead");

    @JsonValue
    private final String name;

    Level(String name) {
        this.name = name;
    }
}
