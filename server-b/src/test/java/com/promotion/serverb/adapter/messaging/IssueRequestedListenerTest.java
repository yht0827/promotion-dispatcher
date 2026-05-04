package com.promotion.serverb.adapter.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

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
		IssueRequestedRetryHandler retryHandler = mock(IssueRequestedRetryHandler.class);
		IssueRequestedListener listener = new IssueRequestedListener(objectMapper, useCase, retryHandler);
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
		verifyNoInteractions(retryHandler);
	}

	@Test
	void handleIssueRequestedMessageDelegatesFailureToRetryHandler() throws Exception {
		FailingProcessCouponIssueUseCase useCase = new FailingProcessCouponIssueUseCase();
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		IssueRequestedRetryHandler retryHandler = mock(IssueRequestedRetryHandler.class);
		IssueRequestedListener listener = new IssueRequestedListener(objectMapper, useCase, retryHandler);
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

		verify(channel, never()).basicAck(anyLong(), anyBoolean());
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
		verify(retryHandler).handleFailure(message, channel, 20L);
	}

	private static Message message(String payload, long deliveryTag) {
		MessageProperties properties = new MessageProperties();
		properties.setDeliveryTag(deliveryTag);
		return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
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
