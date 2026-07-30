package ru.workbit.speech.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import ru.workbit.speech.ws.SttHandshakeInterceptor;
import ru.workbit.speech.ws.SttWebSocketHandler;

import java.util.List;

/**
 * Публикация распознавания наружу: адрес ручки, лимит по IP и размер буфера под аудио.
 * Источник рукопожатия Spring проверяет сам — WebSocket правилам CORS не подчиняется;
 * список берётся из общей настройки {@code app.security.cors.allowed-origins},
 * чтобы он не разъехался с REST.
 */
@Configuration
@EnableWebSocket
public class SpeechWebSocketConfig implements WebSocketConfigurer {
    private static final String STT_PATH = "/api/v1/speech/stt";
    private static final int MAX_BINARY_BUFFER = 64 * 1024;

    private final SttWebSocketHandler handler;
    private final SttHandshakeInterceptor handshakeInterceptor;
    private final String[] allowedOrigins;

    public SpeechWebSocketConfig(SttWebSocketHandler handler,
                                 SttHandshakeInterceptor handshakeInterceptor,
                                 @Value("${app.security.cors.allowed-origins}") List<String> allowedOrigins) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.allowedOrigins = allowedOrigins.toArray(String[]::new);
    }

    /**
     * Вешает обработчик распознавания на {@code /api/v1/speech/stt}.
     *
     * @param registry реестр обработчиков WebSocket
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, STT_PATH)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(allowedOrigins);
    }

    /**
     * Поднимает предел бинарного сообщения до 64 КБ: дефолтные 8 КБ меньше чанка в 400 мс,
     * и фрейм с аудио не пролезал бы целиком.
     *
     * @return настройки контейнера WebSocket
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(MAX_BINARY_BUFFER);
        return container;
    }
}
