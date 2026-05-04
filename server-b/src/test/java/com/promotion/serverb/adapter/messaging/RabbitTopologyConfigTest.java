package com.promotion.serverb.adapter.messaging;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class RabbitTopologyConfigTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void declaresIssueRequestedQueueWithDeadLetterQueue() {
		DirectExchange exchange = context.getBean("issueRequestedExchange", DirectExchange.class);
		Queue queue = context.getBean("issueRequestedQueue", Queue.class);
		Binding binding = context.getBean("issueRequestedBinding", Binding.class);
		DirectExchange deadLetterExchange = context.getBean("issueRequestedDeadLetterExchange", DirectExchange.class);
		Queue deadLetterQueue = context.getBean("issueRequestedDeadLetterQueue", Queue.class);

		assertThat(exchange.getName()).isEqualTo("issue.requested.exchange");
		assertThat(queue.getName()).isEqualTo("issue.requested.queue");
		assertThat(queue.getArguments())
			.containsEntry("x-dead-letter-exchange", "issue.requested.dlx")
			.containsEntry("x-dead-letter-routing-key", "issue.requested.dlq");
		assertThat(binding.getRoutingKey()).isEqualTo("issue.requested");
		assertThat(deadLetterExchange.getName()).isEqualTo("issue.requested.dlx");
		assertThat(deadLetterQueue.getName()).isEqualTo("issue.requested.dlq");
	}

	@Test
	void declaresIssueProcessedExchange() {
		DirectExchange exchange = context.getBean("issueProcessedExchange", DirectExchange.class);

		assertThat(exchange.getName()).isEqualTo("issue.processed.exchange");
		assertThat(exchange.isDurable()).isTrue();
	}
}
