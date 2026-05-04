package com.promotion.servera.application.port.out;

public class DuplicatePromotionUserException extends RuntimeException {

	public DuplicatePromotionUserException(Long promotionId, Long userId) {
		super("이미 저장된 프로모션 사용자 요청입니다. promotionId=" + promotionId + ", userId=" + userId);
	}
}
