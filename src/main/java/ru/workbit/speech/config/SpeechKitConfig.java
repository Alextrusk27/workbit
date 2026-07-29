package ru.workbit.speech.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import yandex.cloud.api.ai.stt.v3.RecognizerGrpc;

@Configuration
@EnableConfigurationProperties(SpeechProperties.class)
public class SpeechKitConfig {
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> DATA_LOGGING =
            Metadata.Key.of("x-data-logging-enabled", Metadata.ASCII_STRING_MARSHALLER);

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel speechKitChannel(SpeechProperties props) {
        return ManagedChannelBuilder.forAddress(props.host(), props.port())
                .useTransportSecurity()
                .build();
    }

    @Bean
    public RecognizerGrpc.RecognizerStub recognizerStub(ManagedChannel speechKitChannel, SpeechProperties props) {
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, "Api-Key " + props.apiKey());
        headers.put(DATA_LOGGING, "false");
        return RecognizerGrpc.newStub(speechKitChannel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }
}
