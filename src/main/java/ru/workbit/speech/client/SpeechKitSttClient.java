package ru.workbit.speech.client;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.workbit.speech.dto.SttResult;
import yandex.cloud.api.ai.stt.v3.RecognizerGrpc;
import yandex.cloud.api.ai.stt.v3.Stt;

/**
 * Клиент потокового распознавания речи Yandex SpeechKit STT v3 (gRPC).
 * Сессия настраивается один раз при открытии стрима: модель {@code general},
 * LINEAR16_PCM 16 кГц моно, русский язык, режим реального времени с нормализацией текста.
 */
@Component
@RequiredArgsConstructor
public class SpeechKitSttClient {
    private static final String MODEL = "general";
    private static final String LANGUAGE = "ru-RU";
    private static final int SAMPLE_RATE_HERTZ = 16_000;
    private static final int CHANNEL_COUNT = 1;

    private final RecognizerGrpc.RecognizerStub recognizerStub;

    /**
     * Открывает двунаправленный стрим распознавания и отправляет в него настройки сессии.
     * Гипотезы приходят в слушателя из потока gRPC, пока сессия не закрыта.
     *
     * @param listener получатель гипотез, ошибок и признака завершения распознавания
     * @return сессия для отправки аудио; закрыть её обязан вызывающий
     */
    public SttSession open(SttListener listener) {
        StreamObserver<Stt.StreamingRequest> requests = recognizerStub.recognizeStreaming(responseObserver(listener));
        requests.onNext(Stt.StreamingRequest.newBuilder()
                .setSessionOptions(sessionOptions())
                .build());
        return new SttSession(requests);
    }

    private static StreamObserver<Stt.StreamingResponse> responseObserver(SttListener listener) {
        return new StreamObserver<>() {

            @Override
            public void onNext(Stt.StreamingResponse response) {
                if (response.hasPartial()) {
                    emit(SttResult.Kind.PARTIAL, response.getPartial());
                } else if (response.hasFinal()) {
                    emit(SttResult.Kind.FINAL, response.getFinal());
                } else if (response.hasFinalRefinement()) {
                    emit(SttResult.Kind.REFINEMENT, response.getFinalRefinement().getNormalizedText());
                }
            }

            @Override
            public void onError(Throwable error) {
                listener.onError(error);
            }

            @Override
            public void onCompleted() {
                listener.onCompleted();
            }

            private void emit(SttResult.Kind kind, Stt.AlternativeUpdate update) {
                String text = firstText(update);
                if (!text.isBlank()) {
                    listener.onResult(new SttResult(kind, text));
                }
            }
        };
    }

    private static String firstText(Stt.AlternativeUpdate update) {
        return update.getAlternativesCount() == 0 ? "" : update.getAlternatives(0).getText();
    }

    private static Stt.StreamingOptions sessionOptions() {
        return Stt.StreamingOptions.newBuilder()
                .setRecognitionModel(Stt.RecognitionModelOptions.newBuilder()
                        .setModel(MODEL)
                        .setAudioFormat(Stt.AudioFormatOptions.newBuilder()
                                .setRawAudio(Stt.RawAudio.newBuilder()
                                        .setAudioEncoding(Stt.RawAudio.AudioEncoding.LINEAR16_PCM)
                                        .setSampleRateHertz(SAMPLE_RATE_HERTZ)
                                        .setAudioChannelCount(CHANNEL_COUNT)))
                        .setTextNormalization(Stt.TextNormalizationOptions.newBuilder()
                                .setTextNormalization(Stt.TextNormalizationOptions.TextNormalization.TEXT_NORMALIZATION_ENABLED))
                        .setLanguageRestriction(Stt.LanguageRestrictionOptions.newBuilder()
                                .setRestrictionType(Stt.LanguageRestrictionOptions.LanguageRestrictionType.WHITELIST)
                                .addLanguageCode(LANGUAGE))
                        .setAudioProcessingType(Stt.RecognitionModelOptions.AudioProcessingType.REAL_TIME))
                .build();
    }
}
