package ru.workbit.speech.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import yandex.cloud.api.ai.stt.v3.RecognizerGrpc;

/**
 * Транспорт до SpeechKit: gRPC-канал и стаб распознавателя с постоянными заголовками.
 */
@Configuration
@EnableConfigurationProperties(SpeechProperties.class)
public class SpeechKitConfig {
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> DATA_LOGGING =
            Metadata.Key.of("x-data-logging-enabled", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Один канал на приложение: сессии распознавания живут внутри него мультиплексированно.
     *
     * @param props адрес сервиса распознавания
     * @return канал поверх TLS, закрывается при остановке контекста
     */
    @Bean(destroyMethod = "shutdown")
    public ManagedChannel speechKitChannel(SpeechProperties props) {
        return ManagedChannelBuilder.forAddress(props.host(), props.port())
                .useTransportSecurity()
                .build();
    }

    /**
     * Стаб с постоянными заголовками: ключ API и явный отказ от сохранения данных.
     * У SpeechKit {@code x-data-logging-enabled} работает не так, как у Foundation Models:
     * сервис по умолчанию аудио не сохраняет, а {@code true} включает сохранение и доработку
     * модели на присланном. Заголовок шлём не для исправления дефолта, а чтобы намерение
     * было видно в коде, если дефолт когда-нибудь поедет.
     *
     * @param speechKitChannel канал до сервиса
     * @param props            ключ API
     * @return асинхронный стаб распознавателя
     */
    @Bean
    public RecognizerGrpc.RecognizerStub recognizerStub(ManagedChannel speechKitChannel, SpeechProperties props) {
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, "Api-Key " + props.apiKey());
        headers.put(DATA_LOGGING, "false");
        return RecognizerGrpc.newStub(speechKitChannel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }
}
