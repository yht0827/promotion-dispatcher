package com.promotion.servera.adapter.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitOperations;

import com.promotion.servera.domain.model.OutboxEvent;

class RabbitIssueRequestedPublisherTest {

	@Test
	void publishSendsIssueRequestedEventAfterRabbitMqConfirm() {
		RabbitOperations rabbitOperations = mock(RabbitOperations.class);
		IssueRequestedRabbitProperties properties = new IssueRequestedRabbitProperties(
			"issue.requested.exchange",
			"issue.requested"
		);
		RabbitIssueRequestedPublisher publisher = new RabbitIssueRequestedPublisher(rabbitOperations, properties);
		OutboxEvent event = OutboxEvent.pendingIssueRequested(
			"event-1",
			"request-1",
			"{\"requestId\":\"request-1\"}",
			LocalDateTime.parse("2026-05-04T03:00:00")
		);

		publisher.publish(event);

		ArgumentCaptor<RabbitOperations.OperationsCallback<Void>> callbackCaptor = operationsCallbackCaptor();
		verify(rabbitOperations).invoke(callbackCaptor.capture());

		RabbitOperations operations = mock(RabbitOperations.class);
		callbackCaptor.getValue().doInRabbit(operations);

		ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
		verify(operations).convertAndSend(
			eq("issue.requested.exchange"),
			eq("issue.requested"),
			eq("{\"requestId\":\"request-1\"}"),
			processorCaptor.capture()
		);
		verify(operations).waitForConfirmsOrDie(5000);

		Message message = MessageBuilder
			.withBody(event.payloadJson().getBytes(StandardCharsets.UTF_8))
			.build();
		Message processed = processorCaptor.getValue().postProcessMessage(message);
		MessageProperties messageProperties = processed.getMessageProperties();
		assertThat(messageProperties.getMessageId()).isEqualTo("event-1");
		assertThat(messageProperties.getType()).isEqualTo("issue.requested");
		assertThat(messageProperties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static ArgumentCaptor<RabbitOperations.OperationsCallback<Void>> operationsCallbackCaptor() {
		return ArgumentCaptor.forClass((Class)RabbitOperations.OperationsCallback.class);
	}
}
