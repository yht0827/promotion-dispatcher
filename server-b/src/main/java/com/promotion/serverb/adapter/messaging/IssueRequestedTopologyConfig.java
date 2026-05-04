package com.promotion.serverb.adapter.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class IssueRequestedTopologyConfig {

	@Bean
	DirectExchange issueRequestedExchange(IssueRequestedRabbitProperties properties) {
		return new DirectExchange(properties.exchange(), true, false);
	}

	@Bean
	Queue issueRequestedQueue(IssueRequestedRabbitProperties properties) {
		return QueueBuilder.durable(properties.queue())
			.deadLetterExchange(properties.retryExchange())
			.deadLetterRoutingKey(properties.retryRoutingKey())
			.build();
	}

	@Bean
	Binding issueRequestedBinding(
		Queue issueRequestedQueue,
		DirectExchange issueRequestedExchange,
		IssueRequestedRabbitProperties properties
	) {
		return BindingBuilder.bind(issueRequestedQueue)
			.to(issueRequestedExchange)
			.with(properties.routingKey());
	}

	@Bean
	DirectExchange issueRequestedRetryExchange(IssueRequestedRabbitProperties properties) {
		return new DirectExchange(properties.retryExchange(), true, false);
	}

	@Bean
	Queue issueRequestedRetryQueue(IssueRequestedRabbitProperties properties) {
		return QueueBuilder.durable(properties.retryQueue())
			.ttl(properties.retryDelayMillis())
			.deadLetterExchange(properties.exchange())
			.deadLetterRoutingKey(properties.routingKey())
			.build();
	}

	@Bean
	Binding issueRequestedRetryBinding(
		Queue issueRequestedRetryQueue,
		DirectExchange issueRequestedRetryExchange,
		IssueRequestedRabbitProperties properties
	) {
		return BindingBuilder.bind(issueRequestedRetryQueue)
			.to(issueRequestedRetryExchange)
			.with(properties.retryRoutingKey());
	}

	@Bean
	DirectExchange issueRequestedDeadLetterExchange(IssueRequestedRabbitProperties properties) {
		return new DirectExchange(properties.deadLetterExchange(), true, false);
	}

	@Bean
	Queue issueRequestedDeadLetterQueue(IssueRequestedRabbitProperties properties) {
		return QueueBuilder.durable(properties.deadLetterQueue()).build();
	}

	@Bean
	Binding issueRequestedDeadLetterBinding(
		Queue issueRequestedDeadLetterQueue,
		DirectExchange issueRequestedDeadLetterExchange,
		IssueRequestedRabbitProperties properties
	) {
		return BindingBuilder.bind(issueRequestedDeadLetterQueue)
			.to(issueRequestedDeadLetterExchange)
			.with(properties.deadLetterRoutingKey());
	}
}
