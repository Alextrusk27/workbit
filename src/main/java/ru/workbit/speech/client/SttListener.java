package ru.workbit.speech.client;

import ru.workbit.speech.dto.SttResult;

public interface SttListener {

    void onResult(SttResult result);

    void onError(Throwable error);

    void onCompleted();
}
