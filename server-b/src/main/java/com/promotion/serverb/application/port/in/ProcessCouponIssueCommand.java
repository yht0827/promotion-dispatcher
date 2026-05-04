package com.promotion.serverb.application.port.in;

import java.time.Instant;
import java.util.Objects;

import com.promotion.common.event.IssueRequestedEvent;

public record ProcessCouponIssueCommand(
	String requestId,
	Long promotionId,
	Long userId,
	String idempotencyKey,
	Instant requestedAt
) {

	public ProcessCouponIssueCommand {
		requireText(requestId, "요청 ID");
		requirePositive(promotionId, "프로모션 ID");
		requirePositive(userId, "사용자 ID");
		requireText(idempotencyKey, "멱등성 키");
		Objects.requireNonNull(requestedAt, "요청 시각");
	}

	public static ProcessCouponIssueCommand from(IssueRequestedEvent event) {
		return new ProcessCouponIssueCommand(
			event.requestId(),
			event.promotionId(),
			event.userId(),
			event.idempotencyKey(),
			event.requestedAt()
		);
	}

	private static void requireText(String value, String fieldName) {
		Objects.requireNonNull(value, fieldName);
		if (value.isBlank()) {
			throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다");
		}
	}

	private static void requirePositive(Long value, String fieldName) {
		Objects.requireNonNull(value, fieldName);
		if (value <= 0) {
			throw new IllegalArgumentException(fieldName + "는 0보다 커야 합니다");
		}
	}
}
