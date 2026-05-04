package com.promotion.servera.adapter.messaging;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.promotion.servera.application.port.out.OutboxEventPublishPort;
import com.promotion.servera.domain.model.OutboxEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class RabbitIssueRequestedPublisher implements OutboxEventPublishPort {

	private final RabbitTemplate rabbitTemplate;
	private final IssueRequestedRabbitProperties properties;

	@Override
	public void publish(OutboxEvent event) {
		rabbitTemplate.convertAndSend(properties.exchange(), properties.routingKey(), event.payloadJson(), message -> {
			MessageProperties properties = message.getMessageProperties();
			properties.setMessageId(event.eventId());
			properties.setType(event.eventType());
			properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
			return message;
		});
	}
}
