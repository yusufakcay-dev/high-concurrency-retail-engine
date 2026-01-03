package io.github.yusufakcay_dev.product_service;

import io.github.yusufakcay_dev.product_service.event.ProductStockStatusConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
		"spring.kafka.bootstrap-servers=localhost:9999",
		"spring.data.redis.host=localhost",
		"spring.data.redis.port=6666"
})
class ProductServiceApplicationTests {

	@SuppressWarnings("removal")
	@MockBean
	private ProductStockStatusConsumer productStockStatusConsumer;

	@Test
	void contextLoads() {
		// Simple smoke test - verify Spring context loads
	}
}
