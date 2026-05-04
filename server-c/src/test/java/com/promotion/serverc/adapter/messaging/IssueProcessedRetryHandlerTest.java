package com.promotion.serverc.adapter.messaging;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.rabbitmq.client.Channel;

class IssueProcessedRetryHandlerTest {

	@Test
	void handleFailureNacksToRetryQueueWhenRetryIsNotExhausted() throws Exception {
		RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
		IssueProcessedRetryHandler retryHandler = new IssueProcessedRetryHandler(rabbitTemplate, properties());
		Channel channel = mock(Channel.class);
		Message message = message("payload", 20L);

		retryHandler.handleFailure(message, channel, 20L);

		verify(channel).basicNack(20L, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
		verifyNoInteractions(rabbitTemplate);
	}

	@Test
	void handleFailurePublishesToDeadLetterQueueWhenRetryIsExhausted() throws Exception {
		RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
		IssueProcessedRetryHandler retryHandler = new IssueProcessedRetryHandler(rabbitTemplate, properties());
		Channel channel = mock(Channel.class);
		Message message = message("payload", 30L);
		message.getMessageProperties().setHeader("x-death", List.of(Map.of(
			"queue", "issue.processed.queue",
			"count", 3L
		)));

		retryHandler.handleFailure(message, channel, 30L);

		verify(rabbitTemplate).convertAndSend(
			"issue.processed.dlx",
			"issue.processed.dlq",
			"payload"
		);
		verify(channel).basicAck(30L, false);
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	private static Message message(String payload, long deliveryTag) {
		MessageProperties properties = new MessageProperties();
		properties.setDeliveryTag(deliveryTag);
		return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
	}

	private static IssueProcessedRabbitProperties properties() {
		return new IssueProcessedRabbitProperties(
			"issue.processed.exchange",
			"issue.processed.queue",
			"issue.processed",
			"issue.processed.retry.exchange",
			"issue.processed.retry.wait.queue",
			"issue.processed.retry",
			5000,
			3,
			"issue.processed.dlx",
			"issue.processed.dlq",
			"issue.processed.dlq"
		);
	}
}
