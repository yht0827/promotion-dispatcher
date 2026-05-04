package com.promotion.serverb.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.promotion.common.event.IssueProcessedEvent;
import com.promotion.common.type.IssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueResult;

final class IssueProcessedEventFactory {

	private IssueProcessedEventFactory() {
	}

	static IssueProcessedEvent create(ProcessCouponIssueResult result, Clock clock) {
		Objects.requireNonNull(result, "쿠폰 처리 결과");
		Objects.requireNonNull(clock, "시계");
		return new IssueProcessedEvent(
			result.requestId(),
			result.promotionId(),
			result.userId(),
			result.result(),
			reason(result.result()),
			Instant.now(clock)
		);
	}

	private static String reason(IssueResult result) {
		return switch (result) {
			case SUCCESS -> null;
			case DUPLICATE -> "이미 발급된 사용자입니다";
			case SOLD_OUT -> "쿠폰 재고가 소진되었습니다";
			case FAILED -> "쿠폰 발급 처리에 실패했습니다";
		};
	}
}
