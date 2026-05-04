package com.promotion.servera.domain.model;

import java.time.LocalDateTime;

public record OutboxEvent(
	String eventId,
	String aggregateType,
	String aggregateId,
	String eventType,
	String payloadJson,
	OutboxEventStatus status,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	private static final String ISSUE_REQUEST_AGGREGATE_TYPE = "COUPON_ISSUE_REQUEST";
	private static final String ISSUE_REQUESTED_EVENT_TYPE = "issue.requested";

	public static OutboxEvent pendingIssueRequested(
		String eventId,
		String requestId,
		String payloadJson,
		LocalDateTime now
	) {
		return new OutboxEvent(
			eventId,
			ISSUE_REQUEST_AGGREGATE_TYPE,
			requestId,
			ISSUE_REQUESTED_EVENT_TYPE,
			payloadJson,
			OutboxEventStatus.PENDING,
			now,
			now
		);
	}
}
