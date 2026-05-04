package com.promotion.serverb.adapter.messaging;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promotion.common.event.IssueProcessedEvent;
import com.promotion.serverb.application.port.out.IssueProcessedPublisherPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class RabbitIssueProcessedPublisher implements IssueProcessedPublisherPort {

	private static final String EVENT_TYPE = "issue.processed";
	private static final long CONFIRM_TIMEOUT_MILLIS = 5000;

	private final RabbitOperations rabbitOperations;
	private final ObjectMapper objectMapper;
	private final IssueProcessedRabbitProperties properties;

	@Override
	public void publish(IssueProcessedEvent event) {
		String payload = toJson(event);
		rabbitOperations.invoke(operations -> {
			operations.convertAndSend(properties.exchange(), properties.routingKey(), payload, message -> {
				MessageProperties properties = message.getMessageProperties();
				properties.setMessageId(event.requestId());
				properties.setType(EVENT_TYPE);
				properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
				return message;
			});
			operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MILLIS);
			return null;
		});
	}

	private String toJson(IssueProcessedEvent event) {
		try {
			return objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("issue.processed 메시지를 JSON으로 변환할 수 없습니다", exception);
		}
	}
}
