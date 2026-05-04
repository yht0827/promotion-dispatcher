package com.promotion.serverc.adapter.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RabbitTopologyConfig {

	@Bean
	DirectExchange issueProcessedExchange(IssueProcessedRabbitProperties properties) {
		return new DirectExchange(properties.exchange(), true, false);
	}

	@Bean
	Queue issueProcessedQueue(IssueProcessedRabbitProperties properties) {
		return QueueBuilder.durable(properties.queue())
			.deadLetterExchange(properties.retryExchange())
			.deadLetterRoutingKey(properties.retryRoutingKey())
			.build();
	}

	@Bean
	Binding issueProcessedBinding(
		Queue issueProcessedQueue,
		DirectExchange issueProcessedExchange,
		IssueProcessedRabbitProperties properties
	) {
		return BindingBuilder.bind(issueProcessedQueue)
			.to(issueProcessedExchange)
			.with(properties.routingKey());
	}

	@Bean
	DirectExchange issueProcessedRetryExchange(IssueProcessedRabbitProperties properties) {
		return new DirectExchange(properties.retryExchange(), true, false);
	}

	@Bean
	Queue issueProcessedRetryQueue(IssueProcessedRabbitProperties properties) {
		return QueueBuilder.durable(properties.retryQueue())
			.ttl(properties.retryDelayMillis())
			.deadLetterExchange(properties.exchange())
			.deadLetterRoutingKey(properties.routingKey())
			.build();
	}

	@Bean
	Binding issueProcessedRetryBinding(
		Queue issueProcessedRetryQueue,
		DirectExchange issueProcessedRetryExchange,
		IssueProcessedRabbitProperties properties
	) {
		return BindingBuilder.bind(issueProcessedRetryQueue)
			.to(issueProcessedRetryExchange)
			.with(properties.retryRoutingKey());
	}

	@Bean
	DirectExchange issueProcessedDeadLetterExchange(IssueProcessedRabbitProperties properties) {
		return new DirectExchange(properties.deadLetterExchange(), true, false);
	}

	@Bean
	Queue issueProcessedDeadLetterQueue(IssueProcessedRabbitProperties properties) {
		return QueueBuilder.durable(properties.deadLetterQueue()).build();
	}

	@Bean
	Binding issueProcessedDeadLetterBinding(
		Queue issueProcessedDeadLetterQueue,
		DirectExchange issueProcessedDeadLetterExchange,
		IssueProcessedRabbitProperties properties
	) {
		return BindingBuilder.bind(issueProcessedDeadLetterQueue)
			.to(issueProcessedDeadLetterExchange)
			.with(properties.deadLetterRoutingKey());
	}
}
