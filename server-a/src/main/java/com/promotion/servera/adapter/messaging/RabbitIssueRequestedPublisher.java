package com.promotion.servera.adapter.messaging;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Component;

import com.promotion.servera.application.port.out.OutboxEventPublishPort;
import com.promotion.servera.domain.model.OutboxEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class RabbitIssueRequestedPublisher implements OutboxEventPublishPort {

	private static final long CONFIRM_TIMEOUT_MILLIS = 5000;

	private final RabbitOperations rabbitOperations;
	private final IssueRequestedRabbitProperties properties;

	@Override
	public void publish(OutboxEvent event) {
		rabbitOperations.invoke(operations -> {
			operations.convertAndSend(properties.exchange(), properties.routingKey(), event.payloadJson(), message -> {
				MessageProperties properties = message.getMessageProperties();
				properties.setMessageId(event.eventId());
				properties.setType(event.eventType());
				properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
				return message;
			});
			operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MILLIS);
			return null;
		});
	}
}
