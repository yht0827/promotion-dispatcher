package com.promotion.serverb.adapter.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.promotion.common.type.IssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueCommand;
import com.promotion.serverb.application.port.in.ProcessCouponIssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueUseCase;

class IssueRequestedListenerTest {

	@Test
	void handleIssueRequestedMessageCallsUseCase() throws Exception {
		FakeProcessCouponIssueUseCase useCase = new FakeProcessCouponIssueUseCase();
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
		IssueRequestedListener listener = new IssueRequestedListener(objectMapper, useCase, rabbitTemplate, properties());
		Channel channel = mock(Channel.class);
		Message message = message("""
			{
			  "requestId": "request-1",
			  "promotionId": 1,
			  "userId": 100,
			  "idempotencyKey": "idem-1",
			  "requestedAt": "2026-05-04T03:00:00Z"
			}
			""", 10L);

		listener.handle(message, channel);

		assertThat(useCase.command).isEqualTo(new ProcessCouponIssueCommand(
			"request-1",
			1L,
			100L,
			"idem-1",
			Instant.parse("2026-05-04T03:00:00Z")
		));
		verify(channel).basicAck(10L, false);
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	@Test
	void handleIssueRequestedMessageNacksWhenProcessingFails() throws Exception {
		FailingProcessCouponIssueUseCase useCase = new FailingProcessCouponIssueUseCase();
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
		IssueRequestedListener listener = new IssueRequestedListener(objectMapper, useCase, rabbitTemplate, properties());
		Channel channel = mock(Channel.class);
		Message message = message("""
			{
			  "requestId": "request-1",
			  "promotionId": 1,
			  "userId": 100,
			  "idempotencyKey": "idem-1",
			  "requestedAt": "2026-05-04T03:00:00Z"
			}
			""", 20L);

		assertThatCode(() -> listener.handle(message, channel))
			.doesNotThrowAnyException();

		verify(channel).basicNack(20L, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
		verifyNoInteractions(rabbitTemplate);
	}

	@Test
	void handleIssueRequestedMessagePublishesToDeadLetterQueueWhenRetryIsExhausted() throws Exception {
		FailingProcessCouponIssueUseCase useCase = new FailingProcessCouponIssueUseCase();
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
		IssueRequestedListener listener = new IssueRequestedListener(objectMapper, useCase, rabbitTemplate, properties());
		Channel channel = mock(Channel.class);
		Message message = message("""
			{
			  "requestId": "request-1",
			  "promotionId": 1,
			  "userId": 100,
			  "idempotencyKey": "idem-1",
			  "requestedAt": "2026-05-04T03:00:00Z"
			}
			""", 30L);
		message.getMessageProperties().setHeader("x-death", List.of(Map.of(
			"queue", "issue.requested.queue",
			"count", 3L
		)));

		assertThatCode(() -> listener.handle(message, channel))
			.doesNotThrowAnyException();

		verify(rabbitTemplate).convertAndSend(
			eq("issue.requested.dlx"),
			eq("issue.requested.dlq"),
			any(String.class)
		);
		verify(channel).basicAck(30L, false);
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	private static Message message(String payload, long deliveryTag) {
		MessageProperties properties = new MessageProperties();
		properties.setDeliveryTag(deliveryTag);
		return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
	}

	private static IssueRequestedRabbitProperties properties() {
		return new IssueRequestedRabbitProperties(
			"issue.requested.exchange",
			"issue.requested.queue",
			"issue.requested",
			"issue.requested.retry.exchange",
			"issue.requested.retry.wait.queue",
			"issue.requested.retry",
			5000,
			3,
			"issue.requested.dlx",
			"issue.requested.dlq",
			"issue.requested.dlq"
		);
	}

	private static class FakeProcessCouponIssueUseCase implements ProcessCouponIssueUseCase {

		private ProcessCouponIssueCommand command;

		@Override
		public ProcessCouponIssueResult process(ProcessCouponIssueCommand command) {
			this.command = command;
			return new ProcessCouponIssueResult(
				command.requestId(),
				command.promotionId(),
				command.userId(),
				IssueResult.SUCCESS
			);
		}
	}

	private static class FailingProcessCouponIssueUseCase implements ProcessCouponIssueUseCase {

		@Override
		public ProcessCouponIssueResult process(ProcessCouponIssueCommand command) {
			throw new IllegalStateException("처리 실패");
		}
	}
}
