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
class IssueRequestedTopologyConfigTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void declaresIssueRequestedQueueWithDeadLetterQueue() {
		DirectExchange exchange = context.getBean("issueRequestedExchange", DirectExchange.class);
		Queue queue = context.getBean("issueRequestedQueue", Queue.class);
		Binding binding = context.getBean("issueRequestedBinding", Binding.class);
		DirectExchange retryExchange = context.getBean("issueRequestedRetryExchange", DirectExchange.class);
		Queue retryQueue = context.getBean("issueRequestedRetryQueue", Queue.class);
		Binding retryBinding = context.getBean("issueRequestedRetryBinding", Binding.class);
		DirectExchange deadLetterExchange = context.getBean("issueRequestedDeadLetterExchange", DirectExchange.class);
		Queue deadLetterQueue = context.getBean("issueRequestedDeadLetterQueue", Queue.class);

		assertThat(exchange.getName()).isEqualTo("issue.requested.exchange");
		assertThat(queue.getName()).isEqualTo("issue.requested.queue");
		assertThat(queue.getArguments())
			.containsEntry("x-dead-letter-exchange", "issue.requested.retry.exchange")
			.containsEntry("x-dead-letter-routing-key", "issue.requested.retry");
		assertThat(binding.getRoutingKey()).isEqualTo("issue.requested");
		assertThat(retryExchange.getName()).isEqualTo("issue.requested.retry.exchange");
		assertThat(retryQueue.getName()).isEqualTo("issue.requested.retry.wait.queue");
		assertThat(retryQueue.getArguments())
			.containsEntry("x-message-ttl", 5000)
			.containsEntry("x-dead-letter-exchange", "issue.requested.exchange")
			.containsEntry("x-dead-letter-routing-key", "issue.requested");
		assertThat(retryBinding.getRoutingKey()).isEqualTo("issue.requested.retry");
		assertThat(deadLetterExchange.getName()).isEqualTo("issue.requested.dlx");
		assertThat(deadLetterQueue.getName()).isEqualTo("issue.requested.dlq");
	}
}
