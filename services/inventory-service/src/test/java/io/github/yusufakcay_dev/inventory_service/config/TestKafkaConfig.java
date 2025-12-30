package io.github.yusufakcay_dev.inventory_service.config;

import io.github.yusufakcay_dev.inventory_service.dto.ProductCreatedEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestKafkaConfig {

    @Bean
    @Primary
    public KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, ProductCreatedEvent> mockTemplate = mock(KafkaTemplate.class);

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, ProductCreatedEvent>> future = CompletableFuture
                .completedFuture(mock(SendResult.class));

        when(mockTemplate.send(anyString(), anyString(), any(ProductCreatedEvent.class)))
                .thenReturn(future);

        return mockTemplate;
    }
}
