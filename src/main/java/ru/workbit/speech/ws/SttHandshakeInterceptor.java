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

@Component
@RequiredArgsConstructor
public class SttHandshakeInterceptor implements HandshakeInterceptor {
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties properties;

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

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
    }
}
