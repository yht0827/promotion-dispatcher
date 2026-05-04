package com.promotion.servera.application.port.out;

public class DuplicateIdempotencyKeyException extends RuntimeException {

	public DuplicateIdempotencyKeyException(String idempotencyKey) {
		super("이미 저장된 멱등성 키입니다. idempotencyKey=" + idempotencyKey);
	}
}
