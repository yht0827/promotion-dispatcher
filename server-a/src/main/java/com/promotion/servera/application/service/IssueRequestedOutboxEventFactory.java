package com.promotion.servera.application.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promotion.common.event.IssueRequestedEvent;
import com.promotion.servera.domain.model.CouponIssueRequest;
import com.promotion.servera.domain.model.OutboxEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class IssueRequestedOutboxEventFactory {

	private final ObjectMapper objectMapper;

	OutboxEvent create(CouponIssueRequest request, Instant requestedAt) {
		IssueRequestedEvent event = new IssueRequestedEvent(
			request.requestId(),
			request.promotionId(),
			request.userId(),
			request.idempotencyKey(),
			requestedAt
		);

		return OutboxEvent.pendingIssueRequested(
			UUID.randomUUID().toString(),
			request.requestId(),
			toJson(event),
			request.createdAt()
		);
	}

	private String toJson(IssueRequestedEvent event) {
		try {
			return objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("발급 요청 이벤트를 JSON으로 변환할 수 없습니다", exception);
		}
	}
}
