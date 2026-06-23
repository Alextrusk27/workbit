package ru.workbit.interview.model;

import lombok.Getter;

@Getter
public enum Level {
    JUNIOR("Junior"),
    MIDDLE("Middle"),
    SENIOR("Senior"),
    LEAD("Lead");

    private final String name;

    Level(String name) {
        this.name = name;
    }
}
