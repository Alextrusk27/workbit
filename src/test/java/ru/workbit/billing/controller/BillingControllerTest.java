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
import ru.workbit.billing.dto.UsageResponse;
import ru.workbit.billing.model.BillingAccount;
import ru.workbit.billing.model.UsageEvent;
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
            var response = new QuotaResponse(BillingAccount.Plan.FREE, expiresAt, 1, 3);
            when(quotaService.getQuota(USER_ID)).thenReturn(response);

            // when / then
            mvc.perform(get(BASE + "/quota")
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plan").value("FREE"))
                    .andExpect(jsonPath("$.planExpiresAt").value("2026-09-01T00:00:00Z"))
                    .andExpect(jsonPath("$.planInterviewsLeft").value(1))
                    .andExpect(jsonPath("$.planTrainingsLeft").value(3));
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

    // -------------------------------------------------------------------------
    // GET /usage
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetUsage")
    class GetUsage {

        @Test
        @DisplayName("Возвращает 200 со счётчиками и историей операций")
        void returns200WithUsage() throws Exception {
            // given
            var at = Instant.parse("2026-08-10T12:00:00Z");
            var response = new UsageResponse(
                    new UsageResponse.UsageCounter(1, 1),
                    new UsageResponse.UsageCounter(2, 3),
                    List.of(new UsageResponse.UsageEventResponse(
                            at, UsageEvent.Kind.SPEND, UsageEvent.Target.TRAINING, 1,
                            "Тренировка — Java, Уверенный")));
            when(quotaService.getUsage(USER_ID)).thenReturn(response);

            // when / then
            mvc.perform(get(BASE + "/usage")
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.interviews.left").value(1))
                    .andExpect(jsonPath("$.interviews.total").value(1))
                    .andExpect(jsonPath("$.trainings.left").value(2))
                    .andExpect(jsonPath("$.trainings.total").value(3))
                    .andExpect(jsonPath("$.events[0].at").value("2026-08-10T12:00:00Z"))
                    .andExpect(jsonPath("$.events[0].kind").value("SPEND"))
                    .andExpect(jsonPath("$.events[0].target").value("TRAINING"))
                    .andExpect(jsonPath("$.events[0].delta").value(1))
                    .andExpect(jsonPath("$.events[0].label").value("Тренировка — Java, Уверенный"));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/usage"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(quotaService);
        }
    }
}
