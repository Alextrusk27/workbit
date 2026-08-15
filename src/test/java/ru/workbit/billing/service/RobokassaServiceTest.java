package ru.workbit.billing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.workbit.billing.config.RobokassaProperties;
import ru.workbit.billing.model.Payment;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RobokassaServiceTest")
class RobokassaServiceTest {

    private static final String MERCHANT_LOGIN = "workbit";
    private static final String PASSWORD1 = "pass1";
    private static final String PASSWORD2 = "pass2";
    private static final String PAYMENT_BASE_URL = "https://auth.robokassa.ru/Merchant/Index.aspx";
    private static final int INV_ID = 42;
    private static final BigDecimal AMOUNT = new BigDecimal("790.00");
    private static final String INIT_SIGNATURE =
            "c9487578adb0a95c4ada1e6a41e6ee8fd054112f90046f1fab837697dfa77c75";
    private static final String RESULT_SIGNATURE =
            "60836e51c173fb0e9179ef1a469437cb64de341713330ed03783aa4e44cb4305";
    private static final String NON_NUMERIC_INV_ID_SIGNATURE =
            "5defa06bbe7c746fd638af17d991f52a3889edea667474ecf7fc191cde9299d4";

    private static RobokassaProperties aProperties(boolean test) {
        return new RobokassaProperties(MERCHANT_LOGIN, PASSWORD1, PASSWORD2, test, PAYMENT_BASE_URL);
    }

    private static Payment aPayment() {
        return Payment.builder()
                .invId(INV_ID)
                .userId(UUID.randomUUID())
                .product(Payment.Product.PLAN_PRO)
                .amount(AMOUNT)
                .status(Payment.Status.PENDING)
                .build();
    }

    private static Map<String, String> notificationParams(String outSum, String invId, String signature) {
        Map<String, String> params = new HashMap<>();
        params.put("OutSum", outSum);
        params.put("InvId", invId);
        params.put("SignatureValue", signature);
        return params;
    }

    private static Map<String, String> queryParams(String url) {
        String query = URI.create(url).getRawQuery();
        return Arrays.stream(query.split("&"))
                .map(param -> param.split("=", 2))
                .collect(Collectors.toMap(
                        p -> URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                        p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }

    @Nested
    @DisplayName("PaymentUrl")
    class PaymentUrlTests {

        private final RobokassaService service = new RobokassaService(aProperties(false));

        @Test
        @DisplayName("Содержит все обязательные параметры инициации платежа, OutSum в виде toPlainString")
        void containsRequiredParams() {
            // given
            Payment payment = aPayment();
            String email = "user@example.com";

            // when
            String url = service.paymentUrl(payment, email);

            // then
            Map<String, String> params = queryParams(url);
            assertThat(params.get("MerchantLogin")).isEqualTo(MERCHANT_LOGIN);
            assertThat(params.get("OutSum")).isEqualTo("790.00");
            assertThat(params.get("InvId")).isEqualTo(String.valueOf(INV_ID));
            assertThat(params.get("Description")).isEqualTo(Payment.Product.PLAN_PRO.getDescription());
            assertThat(params.get("SignatureValue")).isEqualTo(INIT_SIGNATURE);
            assertThat(params.get("Culture")).isEqualTo("ru");
            assertThat(params.get("Email")).isEqualTo(email);
        }

        @Test
        @DisplayName("SignatureValue считается как sha256(MerchantLogin:OutSum:InvId:password1) — референсный вектор")
        void signatureMatchesReferenceVector() {
            // when
            String url = service.paymentUrl(aPayment(), "user@example.com");

            // then
            assertThat(queryParams(url).get("SignatureValue")).isEqualTo(INIT_SIGNATURE);
        }

        @Test
        @DisplayName("IsTest=1 присутствует только когда test=true")
        void includesIsTestOnlyWhenEnabled() {
            // given
            RobokassaService testService = new RobokassaService(aProperties(true));

            // when
            String url = testService.paymentUrl(aPayment(), "user@example.com");

            // then
            assertThat(queryParams(url)).containsEntry("IsTest", "1");
        }

        @Test
        @DisplayName("IsTest отсутствует, когда test=false")
        void omitsIsTestWhenDisabled() {
            // when
            String url = service.paymentUrl(aPayment(), "user@example.com");

            // then
            assertThat(queryParams(url)).doesNotContainKey("IsTest");
        }
    }

    @Nested
    @DisplayName("ParseNotification")
    class ParseNotificationTests {

        private final RobokassaService service = new RobokassaService(aProperties(false));

        @Test
        @DisplayName("Валидное уведомление (нижний регистр подписи) — Notification(invId, amount как пришёл)")
        void returnsNotificationForValidLowerCaseSignature() {
            // when
            PaymentProvider.Notification notification =
                    service.parseNotification(notificationParams("790.00", "42", RESULT_SIGNATURE));

            // then
            assertThat(notification.invId()).isEqualTo(42);
            assertThat(notification.amount()).isEqualByComparingTo("790.00");
        }

        @Test
        @DisplayName("Валидное уведомление (верхний регистр подписи) — сравнение регистронезависимое")
        void returnsNotificationForValidUpperCaseSignature() {
            // when
            PaymentProvider.Notification notification = service.parseNotification(
                    notificationParams("790.00", "42", RESULT_SIGNATURE.toUpperCase(Locale.ROOT)));

            // then
            assertThat(notification.invId()).isEqualTo(42);
            assertThat(notification.amount()).isEqualByComparingTo("790.00");
        }

        @Test
        @DisplayName("Битая подпись — IllegalArgumentException")
        void throwsForTamperedSignature() {
            // given
            String tampered = "0" + RESULT_SIGNATURE.substring(1);

            // when / then
            assertThatThrownBy(() -> service.parseNotification(notificationParams("790.00", "42", tampered)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Подпись посчитана для другой суммы — IllegalArgumentException")
        void throwsForDifferentAmount() {
            assertThatThrownBy(() ->
                    service.parseNotification(notificationParams("790.01", "42", RESULT_SIGNATURE)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Отсутствует OutSum — IllegalArgumentException")
        void throwsWhenOutSumMissing() {
            Map<String, String> params = notificationParams("790.00", "42", RESULT_SIGNATURE);
            params.remove("OutSum");

            assertThatThrownBy(() -> service.parseNotification(params))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Отсутствует InvId — IllegalArgumentException")
        void throwsWhenInvIdMissing() {
            Map<String, String> params = notificationParams("790.00", "42", RESULT_SIGNATURE);
            params.remove("InvId");

            assertThatThrownBy(() -> service.parseNotification(params))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Отсутствует SignatureValue — IllegalArgumentException")
        void throwsWhenSignatureValueMissing() {
            Map<String, String> params = notificationParams("790.00", "42", RESULT_SIGNATURE);
            params.remove("SignatureValue");

            assertThatThrownBy(() -> service.parseNotification(params))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Нецелочисленный InvId с корректной подписью — NumberFormatException (подкласс IAE)")
        void throwsNumberFormatExceptionForNonNumericInvId() {
            assertThatThrownBy(() -> service.parseNotification(
                    notificationParams("790.00", "abc", NON_NUMERIC_INV_ID_SIGNATURE)))
                    .isInstanceOf(NumberFormatException.class)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("NotificationResponse")
    class NotificationResponseTests {

        private final RobokassaService service = new RobokassaService(aProperties(false));

        @Test
        @DisplayName("Возвращает \"OK\" + invId")
        void returnsOkPrefixedInvId() {
            assertThat(service.notificationResponse(INV_ID)).isEqualTo("OK42");
        }
    }
}
