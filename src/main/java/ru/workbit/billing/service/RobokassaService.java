package ru.workbit.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import ru.workbit.billing.config.RobokassaProperties;
import ru.workbit.billing.model.Payment;
import tools.jackson.databind.ObjectMapper;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobokassaService implements PaymentProvider {

    private static final String RESULT_OK = "0";
    private static final String STATE_PAID = "100";
    private static final String TAX_NONE = "none";

    private final RobokassaProperties properties;
    private final RestClient robokassaRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public String paymentUrl(Payment payment, String email) {
        String outSum = payment.getAmount().toPlainString();
        String invId = String.valueOf(payment.getInvId());
        String receipt = encodedReceipt(payment);
        String signature = sha256Hex(String.join(":",
                properties.merchantLogin(), outSum, invId, receipt, properties.password1()));

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.paymentUrl())
                .queryParam("MerchantLogin", properties.merchantLogin())
                .queryParam("OutSum", outSum)
                .queryParam("InvId", invId)
                .queryParam("Description", payment.getProduct().getDescription())
                .queryParam("Receipt", receipt)
                .queryParam("SignatureValue", signature)
                .queryParam("Culture", "ru")
                .queryParam("Email", email);
        if (properties.test()) {
            builder.queryParam("IsTest", 1);
        }
        return builder.encode().toUriString();
    }

    @Override
    public Notification parseNotification(Map<String, String> params) {
        String outSum = params.get("OutSum");
        String invId = params.get("InvId");
        String signature = params.get("SignatureValue");
        if (outSum == null || invId == null || signature == null) {
            throw new IllegalArgumentException("Missing notification parameters");
        }

        String expected = sha256Hex(String.join(":", outSum, invId, properties.password2()));
        boolean valid = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            log.warn("Robokassa notification with invalid signature, invId {}", invId);
            throw new IllegalArgumentException("Invalid signature");
        }
        return new Notification(Integer.parseInt(invId), new BigDecimal(outSum));
    }

    @Override
    public String notificationResponse(int invId) {
        return "OK" + invId;
    }

    @Override
    public boolean isPaid(Payment payment) {
        if (properties.test()) {
            return false;
        }

        String invId = String.valueOf(payment.getInvId());
        String signature = sha256Hex(String.join(":",
                properties.merchantLogin(), invId, properties.password2()));
        try {
            String xml = robokassaRestClient.get()
                    .uri(builder -> builder
                            .queryParam("MerchantLogin", properties.merchantLogin())
                            .queryParam("InvoiceID", invId)
                            .queryParam("Signature", signature)
                            .build())
                    .retrieve()
                    .body(String.class);
            return isPaidState(xml, payment.getInvId());
        } catch (RestClientException e) {
            log.warn("Robokassa state request failed for invId {}", payment.getInvId(), e);
            return false;
        }
    }

    private static boolean isPaidState(String xml, int invId) {
        Document document = parseXml(xml, invId);
        if (document == null) {
            return false;
        }

        String resultCode = code(document, "Result");
        if (!RESULT_OK.equals(resultCode)) {
            log.warn("Robokassa state request rejected for invId {}: result code {}", invId, resultCode);
            return false;
        }
        return STATE_PAID.equals(code(document, "State"));
    }

    private static Document parseXml(String xml, int invId) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            log.warn("Unreadable Robokassa state response for invId {}", invId, e);
            return null;
        }
    }

    private static String code(Document document, String parent) {
        NodeList parents = document.getElementsByTagName(parent);
        if (parents.getLength() == 0) {
            return null;
        }
        NodeList codes = ((Element) parents.item(0)).getElementsByTagName("Code");
        return codes.getLength() == 0 ? null : codes.item(0).getTextContent().trim();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String encodedReceipt(Payment payment) {
        Receipt receipt = new Receipt(List.of(new ReceiptItem(
                payment.getProduct().getLabel(), 1, payment.getAmount(), TAX_NONE)));
        return URLEncoder.encode(objectMapper.writeValueAsString(receipt), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    record Receipt(List<ReceiptItem> items) {
    }

    record ReceiptItem(String name, int quantity, BigDecimal sum, String tax) {
    }
}
