package com.lazaro.caronnar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "security.jwt.secret=test-jwt-secret-key-with-at-least-32-bytes")
class CaronnarApplicationTests {

	@Test
	void contextLoads() {
	}

}
