package ru.workbit.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.workbit.exception.dto.ApiError;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Workbit API", version = "v1"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    @Bean
    OpenApiCustomizer errorResponsesCustomizer() {
        return openApi -> {
            ModelConverters.getInstance(true).readAll(ApiError.class).forEach(openApi.getComponents()::addSchemas);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(this::addErrorResponses));
        };
    }

    private void addErrorResponses(Operation operation) {
        ApiResponses responses = operation.getResponses();
        responses.forEach((code, response) -> {
            if (code.charAt(0) >= '4') {
                response.setContent(apiErrorContent());
            }
        });
        if (isSecured(operation)) {
            responses.putIfAbsent("401", new ApiResponse().description("Не авторизован"));
        }
        responses.putIfAbsent("500", new ApiResponse().description("Внутренняя ошибка").content(apiErrorContent()));
    }

    private boolean isSecured(Operation operation) {
        return operation.getSecurity() != null
                && operation.getSecurity().stream().anyMatch(requirement -> requirement.containsKey("bearerAuth"));
    }

    private Content apiErrorContent() {
        return new Content().addMediaType("*/*",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError")));
    }
}
