package com.promotion.serverc.adapter.messaging;

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
import com.promotion.common.type.IssueResult;
import com.promotion.serverc.application.port.in.SaveCouponIssueResultCommand;
import com.promotion.serverc.application.port.in.SaveCouponIssueResultUseCase;
import com.rabbitmq.client.Channel;

class IssueProcessedListenerTest {

	@Test
	void handleIssueProcessedMessageCallsUseCaseAndAcks() throws Exception {
		FakeSaveCouponIssueResultUseCase useCase = new FakeSaveCouponIssueResultUseCase();
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		IssueProcessedRetryHandler retryHandler = mock(IssueProcessedRetryHandler.class);
		IssueProcessedListener listener = new IssueProcessedListener(objectMapper, useCase, retryHandler);
		Channel channel = mock(Channel.class);
		Message message = message(payload(), 10L);

		listener.handle(message, channel);

		assertThat(useCase.command).isEqualTo(new SaveCouponIssueResultCommand(
			"request-1",
			1L,
			100L,
			IssueResult.SUCCESS,
			null,
			Instant.parse("2026-05-04T03:00:01Z")
		));
		verify(channel).basicAck(10L, false);
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
		verifyNoInteractions(retryHandler);
	}

	@Test
	void handleIssueProcessedMessageDelegatesFailureToRetryHandler() throws Exception {
		FailingSaveCouponIssueResultUseCase useCase = new FailingSaveCouponIssueResultUseCase();
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		IssueProcessedRetryHandler retryHandler = mock(IssueProcessedRetryHandler.class);
		IssueProcessedListener listener = new IssueProcessedListener(objectMapper, useCase, retryHandler);
		Channel channel = mock(Channel.class);
		Message message = message(payload(), 20L);

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

	private static String payload() {
		return """
			{
			  "requestId": "request-1",
			  "promotionId": 1,
			  "userId": 100,
			  "result": "SUCCESS",
			  "reason": null,
			  "processedAt": "2026-05-04T03:00:01Z"
			}
			""";
	}

	private static class FakeSaveCouponIssueResultUseCase implements SaveCouponIssueResultUseCase {

		private SaveCouponIssueResultCommand command;

		@Override
		public void save(SaveCouponIssueResultCommand command) {
			this.command = command;
		}
	}

	private static class FailingSaveCouponIssueResultUseCase implements SaveCouponIssueResultUseCase {

		@Override
		public void save(SaveCouponIssueResultCommand command) {
			throw new IllegalStateException("저장 실패");
		}
	}
}
