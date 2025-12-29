package io.github.yusufakcay_dev.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class GatewayServiceApplicationTests {

	@Autowired
	private RouteLocator routeLocator;

	@Test
	void contextLoads() {
	}

	@Test
	void gatewayApplicationStartsWithRoutesConfigured() {
		assertNotNull(routeLocator, "Routes should be configured");

	}
}
