package com.promotion.servera.adapter.web;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promotion.servera.application.port.in.IssueCouponCommand;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class IssueCouponRequestMapper {

	private final ObjectMapper objectMapper;

	IssueCouponCommand toCommand(
		Long promotionId,
		Long userId,
		String idempotencyKey,
		IssueCouponRequest request
	) {
		return new IssueCouponCommand(
			promotionId,
			userId,
			idempotencyKey,
			toJson(request)
		);
	}

	private String toJson(IssueCouponRequest request) {
		try {
			return objectMapper.writeValueAsString(request);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("쿠폰 발급 요청 본문을 JSON으로 변환할 수 없습니다", exception);
		}
	}
}
