package io.github.yusufakcay_dev.product_service;

import io.github.yusufakcay_dev.product_service.config.TestKafkaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestKafkaConfig.class)
class ProductServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
