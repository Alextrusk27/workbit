package ru.workbit.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.workbit.billing.dto.QuotaResponse;
import ru.workbit.billing.model.BillingAccount;
import ru.workbit.billing.service.QuotaService;
import ru.workbit.exception.controller.ExceptionController;
import ru.workbit.security.config.SecurityConfig;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.JWTService;
import ru.workbit.security.service.UserDetailsServiceImpl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BillingController.class)
@Import({SecurityConfig.class, ExceptionController.class})
@DisplayName("BillingControllerTest")
class BillingControllerTest {

    private static final String BASE = "/api/v1/billing";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    MockMvc mvc;

    // ObjectMapper создаём напрямую: в SB4 @WebMvcTest не включает JacksonAutoConfiguration автоматически
    private final ObjectMapper om = new ObjectMapper();

    @MockitoBean
    QuotaService quotaService;

    // JWTAuthFilter-зависимости: нужны, чтобы SecurityConfig мог создать фильтр
    @MockitoBean
    JWTService jwtService;

    @MockitoBean
    UserDetailsServiceImpl userDetailsService;

    private CustomUserDetails principal() {
        return new CustomUserDetails(USER_ID, "user@example.com", List.of());
    }

    // -------------------------------------------------------------------------
    // GET /quota
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetQuota")
    class GetQuota {

        @Test
        @DisplayName("Возвращает 200 с тарифом и остатками квот")
        void returns200WithQuota() throws Exception {
            // given
            var expiresAt = Instant.parse("2026-09-01T00:00:00Z");
            var response = new QuotaResponse(BillingAccount.Plan.FREE, expiresAt, 1, 3, 0, 0);
            when(quotaService.getQuota(USER_ID)).thenReturn(response);

            // when / then
            mvc.perform(get(BASE + "/quota")
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plan").value("FREE"))
                    .andExpect(jsonPath("$.planExpiresAt").value("2026-09-01T00:00:00Z"))
                    .andExpect(jsonPath("$.planInterviewsLeft").value(1))
                    .andExpect(jsonPath("$.planTrainingsLeft").value(3))
                    .andExpect(jsonPath("$.packInterviewsLeft").value(0))
                    .andExpect(jsonPath("$.packTrainingsLeft").value(0));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/quota"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(quotaService);
        }
    }
}
