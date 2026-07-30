package ru.workbit.speech.client;

import ru.workbit.speech.dto.SttResult;

/**
 * Получатель событий распознавания. Методы вызываются из потока gRPC,
 * поэтому реализация обязана быть потокобезопасной относительно своего адресата.
 * После {@link #onError} и {@link #onCompleted} событий больше не будет.
 */
public interface SttListener {

    /**
     * Очередная гипотеза распознавания.
     *
     * @param result непустой текст с признаком вида гипотезы
     */
    void onResult(SttResult result);

    /**
     * Распознавание оборвалось: ошибка транспорта, отказ сервиса или исчерпанная квота.
     *
     * @param error причина обрыва
     */
    void onError(Throwable error);

    /**
     * Сервис закрыл стрим: всё присланное аудио распознано.
     */
    void onCompleted();
}
