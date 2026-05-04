package com.promotion.serverb.adapter.messaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promotion.common.event.IssueRequestedEvent;
import com.promotion.serverb.application.port.in.ProcessCouponIssueCommand;
import com.promotion.serverb.application.port.in.ProcessCouponIssueUseCase;
import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class IssueRequestedListener {

	private final ObjectMapper objectMapper;
	private final ProcessCouponIssueUseCase processCouponIssueUseCase;
	private final RabbitTemplate rabbitTemplate;
	private final IssueRequestedRabbitProperties properties;

	@RabbitListener(queues = "${promotion.rabbitmq.issue-requested.queue}")
	void handle(Message message, Channel channel) throws IOException {
		long deliveryTag = message.getMessageProperties().getDeliveryTag();
		try {
			IssueRequestedEvent event = toEvent(payload(message));
			processCouponIssueUseCase.process(ProcessCouponIssueCommand.from(event));
			channel.basicAck(deliveryTag, false);
		} catch (RuntimeException exception) {
			if (isRetryExhausted(message)) {
				publishToDeadLetter(message);
				channel.basicAck(deliveryTag, false);
				return;
			}
			channel.basicNack(deliveryTag, false, false);
		}
	}

	private String payload(Message message) {
		return new String(message.getBody(), StandardCharsets.UTF_8);
	}

	private IssueRequestedEvent toEvent(String payload) {
		try {
			return objectMapper.readValue(payload, IssueRequestedEvent.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("issue.requested 메시지를 읽을 수 없습니다", exception);
		}
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
}
