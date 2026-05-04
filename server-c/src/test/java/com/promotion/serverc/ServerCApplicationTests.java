package com.promotion.serverc;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ServerCApplicationTests {

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
}
