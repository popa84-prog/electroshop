package com.electroshop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("h2")
class ElectroShopApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the full Spring context (JPA, Security, JWT, seeding) starts on H2.
    }
}
