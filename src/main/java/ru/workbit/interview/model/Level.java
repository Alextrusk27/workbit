package ru.workbit.interview.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum Level {
    JUNIOR("Junior", "Начинающий"),
    MIDDLE("Middle", "Уверенный"),
    SENIOR("Senior", "Продвинутый");

    private final String grade;

    @JsonValue
    private final String label;

    Level(String grade, String label) {
        this.grade = grade;
        this.label = label;
    }
}
