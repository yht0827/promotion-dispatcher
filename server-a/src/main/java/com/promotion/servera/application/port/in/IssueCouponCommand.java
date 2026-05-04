package com.promotion.servera.application.port.in;

import java.util.Objects;

public record IssueCouponCommand(
	Long promotionId,
	Long userId,
	String idempotencyKey,
	String payloadJson
) {

	public IssueCouponCommand {
		requirePositive(promotionId, "프로모션 ID");
		requirePositive(userId, "사용자 ID");
		requireText(idempotencyKey, "멱등성 키");
		requireText(payloadJson, "요청 본문");
	}

	private static void requirePositive(Long value, String fieldName) {
		Objects.requireNonNull(value, fieldName);
		if (value <= 0) {
			throw new IllegalArgumentException(fieldName + "는 0보다 커야 합니다");
		}
	}

	private static void requireText(String value, String fieldName) {
		Objects.requireNonNull(value, fieldName);
		if (value.isBlank()) {
			throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다");
		}
	}
}
