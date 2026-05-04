package com.promotion.servera.application.port.in;

public class DuplicateCouponIssueRequestException extends RuntimeException {

	public DuplicateCouponIssueRequestException(Long promotionId, Long userId) {
		super("이미 접수된 쿠폰 발급 요청입니다. promotionId=" + promotionId + ", userId=" + userId);
	}
}
