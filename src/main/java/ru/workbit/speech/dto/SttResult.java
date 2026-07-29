package ru.workbit.speech.dto;

public record SttResult(Kind kind, String text) {

    public enum Kind {
        PARTIAL,
        FINAL,
        REFINEMENT
    }
}
