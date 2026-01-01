package io.github.yusufakcay_dev.order_service.client;

import io.github.yusufakcay_dev.order_service.dto.PaymentRequest;
import io.github.yusufakcay_dev.order_service.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", url = "${payment-service.url}")
public interface PaymentServiceClient {

    @PostMapping("/internal/payments/create-link")
    PaymentResponse createPaymentLink(@RequestBody PaymentRequest request);
}
