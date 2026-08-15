package ru.workbit.billing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("RobokassaServiceTest")
class RobokassaServiceTest {

    private static final String MERCHANT_LOGIN = "workbit";
    private static final String PASSWORD1 = "pass1";
    private static final String PASSWORD2 = "pass2";
    private static final String PAYMENT_BASE_URL = "https://auth.robokassa.ru/Merchant/Index.aspx";
    private static final String STATE_URL =
            "https://auth.robokassa.ru/Merchant/WebService/Service.asmx/OpStateExt";
    private static final int INV_ID = 42;
    private static final BigDecimal AMOUNT = new BigDecimal("790.00");
    private static final String INIT_SIGNATURE =
            "c9487578adb0a95c4ada1e6a41e6ee8fd054112f90046f1fab837697dfa77c75";
    private static final String RESULT_SIGNATURE =
            "60836e51c173fb0e9179ef1a469437cb64de341713330ed03783aa4e44cb4305";
    private static final String NON_NUMERIC_INV_ID_SIGNATURE =
            "5defa06bbe7c746fd638af17d991f52a3889edea667474ecf7fc191cde9299d4";
    private static final String STATE_SIGNATURE =
            "a083360d3d91f2f651e69dc4ec50bd9633bed97100b37757144d76281deb47d5";

    private static RobokassaProperties aProperties(boolean test) {
        return new RobokassaProperties(MERCHANT_LOGIN, PASSWORD1, PASSWORD2, test,
                PAYMENT_BASE_URL, STATE_URL);
    }

    private static RobokassaService aService(boolean test) {
        return new RobokassaService(aProperties(test), RestClient.create(STATE_URL));
    }

    private static RobokassaService aService(boolean test, RestClient restClient) {
        return new RobokassaService(aProperties(test), restClient);
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

        private final RobokassaService service = aService(false);

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
            RobokassaService testService = aService(true);

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

        private final RobokassaService service = aService(false);

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

        private final RobokassaService service = aService(false);

        @Test
        @DisplayName("Возвращает \"OK\" + invId")
        void returnsOkPrefixedInvId() {
            assertThat(service.notificationResponse(INV_ID)).isEqualTo("OK42");
        }
    }

    @Nested
    @DisplayName("IsPaid")
    class IsPaidTests {

        private static final String PAID_XML = """
                <?xml version="1.0" encoding="utf-8"?>
                <OperationStateResponse>
                  <Result>
                    <Code>0</Code>
                  </Result>
                  <State>
                    <Code>100</Code>
                  </State>
                </OperationStateResponse>
                """;
        private static final String NOT_PAID_STATE_XML = """
                <?xml version="1.0" encoding="utf-8"?>
                <OperationStateResponse>
                  <Result>
                    <Code>0</Code>
                  </Result>
                  <State>
                    <Code>5</Code>
                  </State>
                </OperationStateResponse>
                """;
        private static final String REJECTED_RESULT_XML = """
                <?xml version="1.0" encoding="utf-8"?>
                <OperationStateResponse>
                  <Result>
                    <Code>1</Code>
                    <Description>Invalid signature</Description>
                  </Result>
                </OperationStateResponse>
                """;
        private static final String MALFORMED_XML = "<OperationStateResponse><Result><Code>0";
        private static final String XML_WITHOUT_RESULT_AND_STATE = """
                <?xml version="1.0" encoding="utf-8"?>
                <OperationStateResponse>
                  <SomeOtherElement>x</SomeOtherElement>
                </OperationStateResponse>
                """;

        private final RestClient restClient = mock(RestClient.class);
        private final RobokassaService service = aService(false, restClient);

        @SuppressWarnings({"unchecked", "rawtypes"})
        private RestClient.RequestHeadersUriSpec stubResponse(String xml) {
            RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
            RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
            when(restClient.get()).thenReturn(uriSpec);
            when(uriSpec.uri(any(Function.class))).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(String.class)).thenReturn(xml);
            return uriSpec;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void stubException(RuntimeException exception) {
            RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
            RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
            when(restClient.get()).thenReturn(uriSpec);
            when(uriSpec.uri(any(Function.class))).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(String.class)).thenThrow(exception);
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> capturedRequestParams(RestClient.RequestHeadersUriSpec uriSpec) {
            ArgumentCaptor<Function<UriBuilder, URI>> captor = ArgumentCaptor.forClass(Function.class);
            verify(uriSpec).uri(captor.capture());
            URI uri = captor.getValue().apply(UriComponentsBuilder.fromUriString(STATE_URL));
            return queryParams(uri.toString());
        }

        @Test
        @DisplayName("Тестовый режим — сразу false, RestClient не дёргается вовсе")
        void returnsFalseInTestModeWithoutCallingRestClient() {
            // given
            RobokassaService testService = aService(true, restClient);

            // when
            boolean result = testService.isPaid(aPayment());

            // then
            assertThat(result).isFalse();
            verifyNoInteractions(restClient);
        }

        @Test
        @DisplayName("Боевой режим шлёт MerchantLogin/InvoiceID/Signature=sha256(MerchantLogin:InvId:password2) и возвращает true при Result/Code=0, State/Code=100")
        void sendsSignedRequestAndReturnsTrueWhenPaid() {
            // given
            RestClient.RequestHeadersUriSpec uriSpec = stubResponse(PAID_XML);

            // when
            boolean result = service.isPaid(aPayment());

            // then
            assertThat(result).isTrue();
            Map<String, String> params = capturedRequestParams(uriSpec);
            assertThat(params.get("MerchantLogin")).isEqualTo(MERCHANT_LOGIN);
            assertThat(params.get("InvoiceID")).isEqualTo(String.valueOf(INV_ID));
            assertThat(params.get("Signature")).isEqualTo(STATE_SIGNATURE);
        }

        @Test
        @DisplayName("Result/Code=0, State/Code не 100 — false")
        void returnsFalseWhenStateNotPaid() {
            // given
            stubResponse(NOT_PAID_STATE_XML);

            // when / then
            assertThat(service.isPaid(aPayment())).isFalse();
        }

        @Test
        @DisplayName("Result/Code не 0 — false")
        void returnsFalseWhenResultNotOk() {
            // given
            stubResponse(REJECTED_RESULT_XML);

            // when / then
            assertThat(service.isPaid(aPayment())).isFalse();
        }

        @Test
        @DisplayName("Нечитаемый/битый XML — false, без исключения")
        void returnsFalseOnMalformedXmlWithoutThrowing() {
            // given
            stubResponse(MALFORMED_XML);

            // when / then
            assertThat(service.isPaid(aPayment())).isFalse();
        }

        @Test
        @DisplayName("XML без элементов Result/State — false")
        void returnsFalseWhenResultAndStateElementsMissing() {
            // given
            stubResponse(XML_WITHOUT_RESULT_AND_STATE);

            // when / then
            assertThat(service.isPaid(aPayment())).isFalse();
        }

        @Test
        @DisplayName("RestClientException при запросе — false, без исключения")
        void returnsFalseOnRestClientExceptionWithoutThrowing() {
            // given
            stubException(new RestClientException("Robokassa unavailable"));

            // when / then
            assertThat(service.isPaid(aPayment())).isFalse();
        }
    }
}
