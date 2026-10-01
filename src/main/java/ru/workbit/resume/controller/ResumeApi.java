package ru.workbit.resume.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import ru.workbit.resume.dto.ResumeResponse;
import ru.workbit.security.model.CustomUserDetails;

@RequestMapping("/api/v1/resumes")
@Tag(name = "Resumes", description = "Резюме пользователя: исходные файлы")
public interface ResumeApi {

    @Operation(summary = "Список резюме")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Список резюме")
    @GetMapping
    ResponseEntity<List<ResumeResponse>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Загрузить резюме", description = "Сохраняет исходный файл резюме.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Резюме загружено"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос"),
            @ApiResponse(responseCode = "409", description = "Резюме уже 3"),
            @ApiResponse(responseCode = "413", description = "Файл больше 5 МБ"),
            @ApiResponse(responseCode = "422", description = "Формат файла не поддерживается"),
            @ApiResponse(responseCode = "429", description = "Слишком много загрузок")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResumeResponse> upload(
            @Parameter(description = "Файл резюме") @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    ) throws IOException;
}
