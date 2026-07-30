package ru.workbit.speech.client;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import yandex.cloud.api.ai.stt.v3.Stt;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Открытая сессия распознавания: отправляющая половина gRPC-стрима.
 * Закрытие идемпотентно и необратимо — после него отправка молча игнорируется,
 * но сервис ещё присылает финальные гипотезы в {@link SttListener}.
 */
@RequiredArgsConstructor
public class SttSession implements AutoCloseable {
    private final StreamObserver<Stt.StreamingRequest> requests;

    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Отправляет кусок аудио в формате, объявленном при открытии сессии.
     *
     * @param audio сырой LPCM 16 кГц моно
     */
    public void send(byte[] audio) {
        if (closed.get()) {
            return;
        }
        requests.onNext(Stt.StreamingRequest.newBuilder()
                .setChunk(Stt.AudioChunk.newBuilder()
                        .setData(ByteString.copyFrom(audio)))
                .build());
    }

    /**
     * Отправляет отметку тишины вместо самого молчания — сервис учитывает её при
     * определении конца фразы, а сами байты тишины передавать не нужно.
     *
     * @param durationMs длительность тишины в миллисекундах
     */
    public void sendSilence(long durationMs) {
        if (closed.get()) {
            return;
        }
        requests.onNext(Stt.StreamingRequest.newBuilder()
                .setSilenceChunk(Stt.SilenceChunk.newBuilder()
                        .setDurationMs(durationMs))
                .build());
    }

    /**
     * Закрывает отправляющую половину стрима: аудио кончилось, ждём финальные гипотезы.
     * Повторный вызов ничего не делает — иначе второй {@code onCompleted} бросил бы исключение.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            requests.onCompleted();
        }
    }
}
