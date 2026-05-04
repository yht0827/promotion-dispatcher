package com.promotion.serverb.application.port.in;

import java.util.Objects;

import com.promotion.common.type.IssueResult;

public record ProcessCouponIssueResult(
	String requestId,
	Long promotionId,
	Long userId,
	IssueResult result
) {

	public ProcessCouponIssueResult {
		Objects.requireNonNull(requestId, "요청 ID");
		Objects.requireNonNull(promotionId, "프로모션 ID");
		Objects.requireNonNull(userId, "사용자 ID");
		Objects.requireNonNull(result, "처리 결과");
	}

	public static ProcessCouponIssueResult of(ProcessCouponIssueCommand command, IssueResult result) {
		return new ProcessCouponIssueResult(
			command.requestId(),
			command.promotionId(),
			command.userId(),
			result
		);
	}
}
