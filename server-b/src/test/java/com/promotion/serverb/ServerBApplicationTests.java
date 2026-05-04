package com.promotion.serverb;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ServerBApplicationTests {

	@Autowired
	private RabbitProperties rabbitProperties;

	@Test
	void contextLoads() {
	}

	@Test
	void rabbitListenerThroughputSettingsAreBound() {
		RabbitProperties.SimpleContainer simple = rabbitProperties.getListener().getSimple();

		assertThat(simple.getPrefetch()).isEqualTo(50);
		assertThat(simple.getConcurrency()).isEqualTo(2);
		assertThat(simple.getMaxConcurrency()).isEqualTo(8);
	}

	@Test
	void rabbitPublisherConfirmIsEnabled() {
		assertThat(rabbitProperties.getPublisherConfirmType()).isEqualTo(ConfirmType.SIMPLE);
	}
}
