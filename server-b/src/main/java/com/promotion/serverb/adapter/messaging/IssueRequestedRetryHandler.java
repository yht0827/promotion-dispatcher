package com.promotion.serverb.adapter.messaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class IssueRequestedRetryHandler {

	private final RabbitTemplate rabbitTemplate;
	private final IssueRequestedRabbitProperties properties;

	void handleFailure(Message message, Channel channel, long deliveryTag) throws IOException {
		if (isRetryExhausted(message)) {
			publishToDeadLetter(message);
			channel.basicAck(deliveryTag, false);
			return;
		}
		channel.basicNack(deliveryTag, false, false);
	}

	private boolean isRetryExhausted(Message message) {
		return retryCount(message) >= properties.maxRetryCount();
	}

	private long retryCount(Message message) {
		Object xDeathHeader = message.getMessageProperties().getHeaders().get("x-death");
		if (!(xDeathHeader instanceof List<?> xDeathEntries)) {
			return 0;
		}
		return xDeathEntries.stream()
			.filter(Map.class::isInstance)
			.map(Map.class::cast)
			.filter(entry -> properties.queue().equals(entry.get("queue")))
			.map(entry -> entry.get("count"))
			.filter(Number.class::isInstance)
			.map(Number.class::cast)
			.mapToLong(Number::longValue)
			.max()
			.orElse(0);
	}

	private void publishToDeadLetter(Message message) {
		rabbitTemplate.convertAndSend(
			properties.deadLetterExchange(),
			properties.deadLetterRoutingKey(),
			payload(message)
		);
	}

	private String payload(Message message) {
		return new String(message.getBody(), StandardCharsets.UTF_8);
	}
}
