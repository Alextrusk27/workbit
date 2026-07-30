package ru.workbit.speech.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import ru.workbit.exception.TooManyRequestsException;
import ru.workbit.security.config.RateLimitProperties;
import ru.workbit.security.service.RateLimiterService;
import ru.workbit.util.ClientIp;

import java.util.Map;

/**
 * Ограничивает частоту открытия сессий распознавания по IP — бакет {@code stt}.
 * Лимит стоит на handshake, а не на сообщениях: соединение стоит дорого само по себе,
 * а аудио внутри уже ограничено объёмом сессии в {@link SttWebSocketHandler}.
 * Аутентификацию проверяет общая цепочка Spring Security: handshake — обычный HTTP-запрос,
 * кука с access-токеном уходит вместе с ним.
 */
@Component
@RequiredArgsConstructor
public class SttHandshakeInterceptor implements HandshakeInterceptor {
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties properties;

    /**
     * Пропускает handshake, если лимит по IP не исчерпан, иначе отвечает 429 и рвёт рукопожатие.
     *
     * @param request    HTTP-запрос рукопожатия
     * @param response   ответ, в который пишется статус при отказе
     * @param wsHandler  обработчик, к которому подключается клиент
     * @param attributes атрибуты будущей сессии WebSocket
     * @return {@code true}, если соединение разрешено
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return true;
        }
        try {
            rateLimiter.check(ClientIp.from(servletRequest.getServletRequest()), properties.stt());
            return true;
        } catch (TooManyRequestsException e) {
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return false;
        }
    }

    /**
     * После рукопожатия делать нечего — метод обязателен по контракту.
     *
     * @param request   HTTP-запрос рукопожатия
     * @param response  ответ рукопожатия
     * @param wsHandler обработчик, к которому подключился клиент
     * @param exception ошибка рукопожатия, если она была
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
    }
}
