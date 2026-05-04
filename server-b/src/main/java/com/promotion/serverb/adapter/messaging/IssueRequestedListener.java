package com.promotion.serverb.adapter.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promotion.common.event.IssueRequestedEvent;
import com.promotion.serverb.application.port.in.ProcessCouponIssueCommand;
import com.promotion.serverb.application.port.in.ProcessCouponIssueUseCase;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class IssueRequestedListener {

	private final ObjectMapper objectMapper;
	private final ProcessCouponIssueUseCase processCouponIssueUseCase;

	@RabbitListener(queues = "${promotion.rabbitmq.issue-requested.queue}")
	void handle(String payload) {
		IssueRequestedEvent event = toEvent(payload);
		processCouponIssueUseCase.process(new ProcessCouponIssueCommand(
			event.requestId(),
			event.promotionId(),
			event.userId(),
			event.idempotencyKey(),
			event.requestedAt()
		));
	}

	private IssueRequestedEvent toEvent(String payload) {
		try {
			return objectMapper.readValue(payload, IssueRequestedEvent.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("issue.requested 메시지를 읽을 수 없습니다", exception);
		}
	}
}
