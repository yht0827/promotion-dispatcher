package com.promotion.serverc.adapter.messaging;

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
	void declaresIssueProcessedQueueWithDeadLetterQueue() {
		DirectExchange exchange = context.getBean("issueProcessedExchange", DirectExchange.class);
		Queue queue = context.getBean("issueProcessedQueue", Queue.class);
		Binding binding = context.getBean("issueProcessedBinding", Binding.class);
		DirectExchange deadLetterExchange = context.getBean("issueProcessedDeadLetterExchange", DirectExchange.class);
		Queue deadLetterQueue = context.getBean("issueProcessedDeadLetterQueue", Queue.class);

		assertThat(exchange.getName()).isEqualTo("issue.processed.exchange");
		assertThat(queue.getName()).isEqualTo("issue.processed.queue");
		assertThat(queue.getArguments())
			.containsEntry("x-dead-letter-exchange", "issue.processed.dlx")
			.containsEntry("x-dead-letter-routing-key", "issue.processed.dlq");
		assertThat(binding.getRoutingKey()).isEqualTo("issue.processed");
		assertThat(deadLetterExchange.getName()).isEqualTo("issue.processed.dlx");
		assertThat(deadLetterQueue.getName()).isEqualTo("issue.processed.dlq");
	}
}
