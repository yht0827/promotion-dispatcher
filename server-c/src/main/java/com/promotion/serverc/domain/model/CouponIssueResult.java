package com.promotion.serverc.domain.model;

import java.time.Instant;
import java.util.Objects;

import com.promotion.common.type.IssueResult;

public record CouponIssueResult(
	String requestId,
	Long promotionId,
	Long userId,
	IssueResult result,
	String reason,
	Instant processedAt
) {

	public CouponIssueResult {
		requireText(requestId, "요청 ID");
		requirePositive(promotionId, "프로모션 ID");
		requirePositive(userId, "사용자 ID");
		Objects.requireNonNull(result, "처리 결과");
		Objects.requireNonNull(processedAt, "처리 시각");
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
