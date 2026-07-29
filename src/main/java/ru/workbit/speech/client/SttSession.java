package ru.workbit.speech.client;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import yandex.cloud.api.ai.stt.v3.Stt;

@RequiredArgsConstructor
public class SttSession implements AutoCloseable {
    private final StreamObserver<Stt.StreamingRequest> requests;

    public void send(byte[] audio) {
        requests.onNext(Stt.StreamingRequest.newBuilder()
                .setChunk(Stt.AudioChunk.newBuilder()
                        .setData(ByteString.copyFrom(audio)))
                .build());
    }

    public void sendSilence(long durationMs) {
        requests.onNext(Stt.StreamingRequest.newBuilder()
                .setSilenceChunk(Stt.SilenceChunk.newBuilder()
                        .setDurationMs(durationMs))
                .build());
    }

    @Override
    public void close() {
        requests.onCompleted();
    }
}
