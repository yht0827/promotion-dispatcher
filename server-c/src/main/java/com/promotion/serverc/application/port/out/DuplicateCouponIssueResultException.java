package com.promotion.serverc.application.port.out;

public class DuplicateCouponIssueResultException extends RuntimeException {

	public DuplicateCouponIssueResultException(String requestId) {
		super("이미 저장된 쿠폰 발급 결과입니다. requestId=" + requestId);
	}
}
