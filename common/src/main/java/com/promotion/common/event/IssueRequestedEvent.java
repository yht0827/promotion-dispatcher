package com.promotion.common.event;

import java.time.Instant;
import java.util.Objects;

public record IssueRequestedEvent(
	String requestId,
	Long promotionId,
	Long userId,
	String idempotencyKey,
	Instant requestedAt
) {

	public IssueRequestedEvent {
		requireText(requestId, "요청 ID");
		requirePositive(promotionId, "프로모션 ID");
		requirePositive(userId, "사용자 ID");
		requireText(idempotencyKey, "멱등성 키");
		Objects.requireNonNull(requestedAt, "요청 시각");
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
