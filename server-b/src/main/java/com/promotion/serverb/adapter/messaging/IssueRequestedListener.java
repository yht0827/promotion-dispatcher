package com.promotion.serverb.adapter.messaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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
	private final IssueRequestedRetryHandler retryHandler;

	@RabbitListener(queues = "${promotion.rabbitmq.issue-requested.queue}")
	void handle(Message message, Channel channel) throws IOException {
		long deliveryTag = message.getMessageProperties().getDeliveryTag();
		try {
			IssueRequestedEvent event = toEvent(payload(message));
			processCouponIssueUseCase.process(ProcessCouponIssueCommand.from(event));
			channel.basicAck(deliveryTag, false);
		} catch (RuntimeException exception) {
			retryHandler.handleFailure(message, channel, deliveryTag);
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
}
