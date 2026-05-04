package com.promotion.servera;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "promotion.outbox-relay.enabled=false")
class ServerAApplicationTests {

	@Autowired
	private RabbitProperties rabbitProperties;

	@Test
	void contextLoads() {
	}

	@Test
	void rabbitPublisherConfirmIsEnabled() {
		assertThat(rabbitProperties.getPublisherConfirmType()).isEqualTo(ConfirmType.SIMPLE);
	}
}
