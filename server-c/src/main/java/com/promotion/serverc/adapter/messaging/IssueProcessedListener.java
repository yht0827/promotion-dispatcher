package com.promotion.serverc.adapter.messaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promotion.common.event.IssueProcessedEvent;
import com.promotion.serverc.application.port.in.SaveCouponIssueResultCommand;
import com.promotion.serverc.application.port.in.SaveCouponIssueResultUseCase;
import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class IssueProcessedListener {

	private final ObjectMapper objectMapper;
	private final SaveCouponIssueResultUseCase saveCouponIssueResultUseCase;
	private final IssueProcessedRetryHandler retryHandler;

	@RabbitListener(queues = "${promotion.rabbitmq.issue-processed.queue}")
	void handle(Message message, Channel channel) throws IOException {
		long deliveryTag = message.getMessageProperties().getDeliveryTag();
		try {
			IssueProcessedEvent event = toEvent(payload(message));
			saveCouponIssueResultUseCase.save(SaveCouponIssueResultCommand.from(event));
			channel.basicAck(deliveryTag, false);
		} catch (RuntimeException exception) {
			retryHandler.handleFailure(message, channel, deliveryTag);
		}
	}

	private String payload(Message message) {
		return new String(message.getBody(), StandardCharsets.UTF_8);
	}

	private IssueProcessedEvent toEvent(String payload) {
		try {
			return objectMapper.readValue(payload, IssueProcessedEvent.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("issue.processed 메시지를 읽을 수 없습니다", exception);
		}
	}
}
