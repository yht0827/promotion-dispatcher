package com.promotion.servera.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.promotion.servera.application.port.in.IssueCouponResult;
import com.promotion.servera.application.port.in.IssueCouponUseCase;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
class CouponIssueController {

	private final IssueCouponUseCase issueCouponUseCase;
	private final IssueCouponRequestMapper requestMapper;

	@PostMapping("/api/v1/promotions/{promotionId}/coupons/issue")
	ResponseEntity<IssueCouponResponse> issue(
		@PathVariable Long promotionId,
		@RequestHeader("X-User-Id") Long userId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody IssueCouponRequest request
	) {
		IssueCouponResult result = issueCouponUseCase.issue(requestMapper.toCommand(
			promotionId,
			userId,
			idempotencyKey,
			request
		));

		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(new IssueCouponResponse(result.requestId(), result.status()));
	}
}
