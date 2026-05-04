package com.promotion.servera.adapter.messaging;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = "promotion.outbox-relay.enabled=false")
class RabbitTopologyConfigTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void declaresIssueRequestedExchange() {
		DirectExchange exchange = context.getBean("issueRequestedExchange", DirectExchange.class);

		assertThat(exchange.getName()).isEqualTo("issue.requested.exchange");
		assertThat(exchange.isDurable()).isTrue();
	}
}
