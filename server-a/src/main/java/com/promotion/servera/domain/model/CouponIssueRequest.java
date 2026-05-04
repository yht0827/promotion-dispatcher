package com.promotion.servera.domain.model;

import java.time.LocalDateTime;

public record CouponIssueRequest(
	String requestId,
	Long promotionId,
	Long userId,
	String idempotencyKey,
	CouponIssueRequestStatus status,
	String payloadJson,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	public static CouponIssueRequest accepted(
		String requestId,
		Long promotionId,
		Long userId,
		String idempotencyKey,
		String payloadJson,
		LocalDateTime now
	) {
		return new CouponIssueRequest(
			requestId,
			promotionId,
			userId,
			idempotencyKey,
			CouponIssueRequestStatus.ACCEPTED,
			payloadJson,
			now,
			now
		);
	}
}
