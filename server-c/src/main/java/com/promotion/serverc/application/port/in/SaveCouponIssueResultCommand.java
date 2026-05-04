package com.promotion.serverc.application.port.in;

import java.time.Instant;
import java.util.Objects;

import com.promotion.common.event.IssueProcessedEvent;
import com.promotion.common.type.IssueResult;
import com.promotion.serverc.domain.model.CouponIssueResult;

public record SaveCouponIssueResultCommand(
	String requestId,
	Long promotionId,
	Long userId,
	IssueResult result,
	String reason,
	Instant processedAt
) {

	public SaveCouponIssueResultCommand {
		Objects.requireNonNull(requestId, "요청 ID");
		Objects.requireNonNull(promotionId, "프로모션 ID");
		Objects.requireNonNull(userId, "사용자 ID");
		Objects.requireNonNull(result, "처리 결과");
		Objects.requireNonNull(processedAt, "처리 시각");
	}

	public static SaveCouponIssueResultCommand from(IssueProcessedEvent event) {
		return new SaveCouponIssueResultCommand(
			event.requestId(),
			event.promotionId(),
			event.userId(),
			event.result(),
			event.reason(),
			event.processedAt()
		);
	}

	public CouponIssueResult toDomain() {
		return new CouponIssueResult(
			requestId,
			promotionId,
			userId,
			result,
			reason,
			processedAt
		);
	}
}
