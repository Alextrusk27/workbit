package ru.workbit.speech.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import ru.workbit.speech.client.SpeechKitSttClient;
import ru.workbit.speech.client.SttListener;
import ru.workbit.speech.client.SttSession;
import ru.workbit.speech.dto.SttEvent;
import ru.workbit.speech.dto.SttResult;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class SttWebSocketHandler extends AbstractWebSocketHandler {
    private static final String RECOGNITION = "recognition";
    private static final String RECEIVED_BYTES = "receivedBytes";
    private static final String STOP = "stop";
    private static final long MAX_SESSION_BYTES = 4L * 60 * 16_000 * 2;
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT = 256 * 1024;
    private static final CloseStatus RECOGNITION_FAILED =
            CloseStatus.SERVER_ERROR.withReason("Recognition failed");

    private final SpeechKitSttClient client;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession sink =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT);
        session.getAttributes().put(RECEIVED_BYTES, new AtomicLong());
        session.getAttributes().put(RECOGNITION, client.open(new Bridge(sink)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SttSession recognition = recognition(session);
        if (recognition != null) {
            recognition.close();
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        SttSession recognition = recognition(session);
        if (recognition == null) {
            return;
        }
        ByteBuffer payload = message.getPayload();
        byte[] audio = new byte[payload.remaining()];
        payload.get(audio);

        AtomicLong received = (AtomicLong) session.getAttributes().get(RECEIVED_BYTES);
        if (received != null && received.addAndGet(audio.length) > MAX_SESSION_BYTES) {
            log.info("Speech session exceeded the audio limit [session={}]", session.getId());
            recognition.close();
            return;
        }
        recognition.send(audio);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SttSession recognition = recognition(session);
        if (recognition != null && STOP.equals(message.getPayload())) {
            recognition.close();
        }
    }

    private static SttSession recognition(WebSocketSession session) {
        return (SttSession) session.getAttributes().get(RECOGNITION);
    }

    @RequiredArgsConstructor
    private final class Bridge implements SttListener {
        private final WebSocketSession session;

        @Override
        public void onResult(SttResult result) {
            send(SttEvent.of(result));
        }

        @Override
        public void onError(Throwable error) {
            log.warn("Speech recognition failed [session={}]: {}", session.getId(), error.toString());
            send(SttEvent.error("Recognition failed"));
            close(RECOGNITION_FAILED);
        }

        @Override
        public void onCompleted() {
            close(CloseStatus.NORMAL);
        }

        private void send(SttEvent event) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
            } catch (IOException e) {
                log.warn("Failed to deliver speech event [session={}]: {}", session.getId(), e.toString());
            }
        }

        private void close(CloseStatus status) {
            try {
                session.close(status);
            } catch (IOException e) {
                log.warn("Failed to close speech session [session={}]: {}", session.getId(), e.toString());
            }
        }
    }
}
