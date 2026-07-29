package ru.workbit.speech.dto;

public record SttEvent(Type type, String text) {

    public enum Type {
        PARTIAL,
        FINAL,
        REFINEMENT,
        ERROR
    }

    public static SttEvent of(SttResult result) {
        return new SttEvent(Type.valueOf(result.kind().name()), result.text());
    }

    public static SttEvent error(String message) {
        return new SttEvent(Type.ERROR, message);
    }
}
