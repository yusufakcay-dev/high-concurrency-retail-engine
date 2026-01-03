package io.github.yusufakcay_dev.payment_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yusufakcay_dev.payment_service.dto.PaymentRequest;
import io.github.yusufakcay_dev.payment_service.dto.PaymentResponse;
import io.github.yusufakcay_dev.payment_service.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalPaymentController.class)
class InternalPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @SuppressWarnings("removal")
    @MockBean
    private PaymentService paymentService;

    @Test
    void shouldCreatePaymentLinkSuccessfully() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .orderId(UUID.randomUUID())
                .amount(new BigDecimal("99.99"))
                .customerEmail("test@example.com")
                .currency("USD")
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .paymentId(UUID.randomUUID().toString())
                .paymentUrl("https://checkout.stripe.com/pay/cs_test_123")
                .status("PENDING")
                .build();

        when(paymentService.createPaymentLink(any(PaymentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/internal/payments/create-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentUrl").value("https://checkout.stripe.com/pay/cs_test_123"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldRejectWebhookWithoutSignatureHeader() throws Exception {
        mockMvc.perform(post("/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"checkout.session.completed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Missing Stripe-Signature header"));
    }

    @Test
    void shouldRejectWebhookWithInvalidSignature() throws Exception {
        mockMvc.perform(post("/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=1234567890,v1=invalid_signature")
                .content("{\"type\":\"checkout.session.completed\"}"))
                .andExpect(status().isBadRequest());
    }
}
