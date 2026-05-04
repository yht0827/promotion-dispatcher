package com.promotion.serverb.adapter.messaging;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class IssueProcessedTopologyConfigTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void declaresIssueProcessedExchange() {
		DirectExchange exchange = context.getBean("issueProcessedExchange", DirectExchange.class);

		assertThat(exchange.getName()).isEqualTo("issue.processed.exchange");
		assertThat(exchange.isDurable()).isTrue();
	}
}
