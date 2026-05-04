package com.promotion.servera;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "promotion.outbox-relay.enabled=false")
class ServerAApplicationTests {

    @Test
    void contextLoads() {
    }
}
