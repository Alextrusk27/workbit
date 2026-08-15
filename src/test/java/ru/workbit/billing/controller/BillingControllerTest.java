package ru.workbit.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.workbit.billing.dto.PaymentCreateRequest;
import ru.workbit.billing.dto.PaymentCreateResponse;
import ru.workbit.billing.dto.PaymentStatusResponse;
import ru.workbit.billing.dto.QuotaResponse;
import ru.workbit.billing.dto.UsageResponse;
import ru.workbit.billing.model.BillingAccount;
import ru.workbit.billing.model.Payment;
import ru.workbit.billing.model.UsageEvent;
import ru.workbit.billing.service.PaymentService;
import ru.workbit.billing.service.QuotaService;
import ru.workbit.exception.NotFoundException;
import ru.workbit.exception.controller.ExceptionController;
import ru.workbit.security.config.SecurityConfig;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.JWTService;
import ru.workbit.security.service.UserDetailsServiceImpl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @MockitoBean
    PaymentService paymentService;

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

    // -------------------------------------------------------------------------
    // POST /payments
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CreatePayment")
    class CreatePayment {

        @Test
        @DisplayName("Возвращает 200 с идентификатором и URL платежа")
        void returns200WithPayment() throws Exception {
            // given
            var paymentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            var response = new PaymentCreateResponse(paymentId, "https://auth.robokassa.ru/Merchant/Index/1");
            when(paymentService.create(USER_ID, Payment.Product.PLAN_PRO, "user@example.com"))
                    .thenReturn(response);

            // when / then
            mvc.perform(post(BASE + "/payments")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(new PaymentCreateRequest(Payment.Product.PLAN_PRO))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                    .andExpect(jsonPath("$.paymentUrl").value("https://auth.robokassa.ru/Merchant/Index/1"));

            verify(paymentService).create(USER_ID, Payment.Product.PLAN_PRO, "user@example.com");
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(post(BASE + "/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(new PaymentCreateRequest(Payment.Product.PLAN_PRO))))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("Возвращает 400, когда product не указан")
        void returns400WhenProductMissing() throws Exception {
            // when / then
            mvc.perform(post(BASE + "/payments")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("Возвращает 400, когда product невалиден")
        void returns400WhenProductInvalid() throws Exception {
            // when / then
            mvc.perform(post(BASE + "/payments")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"product\":\"INVALID\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(paymentService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /payments/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetPayment")
    class GetPayment {

        private final UUID paymentId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        @Test
        @DisplayName("Возвращает 200 со статусом и продуктом платежа")
        void returns200WithPaymentStatus() throws Exception {
            // given
            var response = new PaymentStatusResponse(Payment.Status.PAID, Payment.Product.PLAN_PRO);
            when(paymentService.get(paymentId, USER_ID)).thenReturn(response);

            // when / then
            mvc.perform(get(BASE + "/payments/" + paymentId)
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.product").value("PLAN_PRO"));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/payments/" + paymentId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("Возвращает 404, когда платёж чужой или не найден")
        void returns404WhenNotFound() throws Exception {
            // given
            when(paymentService.get(paymentId, USER_ID))
                    .thenThrow(new NotFoundException("Payment not found"));

            // when / then
            mvc.perform(get(BASE + "/payments/" + paymentId)
                            .with(user(principal())))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // POST /robokassa/result
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("RobokassaResult")
    class RobokassaResult {

        @Test
        @DisplayName("Возвращает 200 text/plain без аутентификации")
        void returns200WithoutAuthentication() throws Exception {
            // given
            when(paymentService.confirm(Map.of(
                    "OutSum", "790.00",
                    "InvId", "42",
                    "SignatureValue", "somesignature"
            ))).thenReturn("OK42");

            // when / then
            mvc.perform(post(BASE + "/robokassa/result")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("OutSum", "790.00")
                            .param("InvId", "42")
                            .param("SignatureValue", "somesignature"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(content().string("OK42"));
        }

        @Test
        @DisplayName("Возвращает 400, когда подпись невалидна")
        void returns400WhenSignatureInvalid() throws Exception {
            // given
            when(paymentService.confirm(anyMap()))
                    .thenThrow(new IllegalArgumentException("Invalid payment notification"));

            // when / then
            mvc.perform(post(BASE + "/robokassa/result")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("OutSum", "790.00")
                            .param("InvId", "42")
                            .param("SignatureValue", "bad-signature"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Возвращает 400, когда уведомление неполное")
        void returns400WhenNotificationIncomplete() throws Exception {
            // given
            when(paymentService.confirm(anyMap()))
                    .thenThrow(new IllegalArgumentException("Missing required parameter"));

            // when / then
            mvc.perform(post(BASE + "/robokassa/result")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("OutSum", "790.00")
                            .param("SignatureValue", "somesignature"))
                    .andExpect(status().isBadRequest());

            verify(paymentService).confirm(anyMap());
        }
    }
}
