package com.promotion.servera.application.port.in;

public class RateLimitExceededException extends RuntimeException {

	public RateLimitExceededException(Long userId) {
		super("쿠폰 발급 요청 제한을 초과했습니다. userId=" + userId);
	}
}
