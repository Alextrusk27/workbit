package ru.workbit.speech.client;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import yandex.cloud.api.ai.stt.v3.Stt;

import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class SttSession implements AutoCloseable {
    private final StreamObserver<Stt.StreamingRequest> requests;

    private final AtomicBoolean closed = new AtomicBoolean();

    public void send(byte[] audio) {
        if (closed.get()) {
            return;
        }
        requests.onNext(Stt.StreamingRequest.newBuilder()
                .setChunk(Stt.AudioChunk.newBuilder()
                        .setData(ByteString.copyFrom(audio)))
                .build());
    }

    public void sendSilence(long durationMs) {
        if (closed.get()) {
            return;
        }
        requests.onNext(Stt.StreamingRequest.newBuilder()
                .setSilenceChunk(Stt.SilenceChunk.newBuilder()
                        .setDurationMs(durationMs))
                .build());
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            requests.onCompleted();
        }
    }
}
