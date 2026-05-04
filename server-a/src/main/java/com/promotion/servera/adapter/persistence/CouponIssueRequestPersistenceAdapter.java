package com.promotion.servera.adapter.persistence;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.promotion.servera.application.port.out.CouponIssueRequestLoadPort;
import com.promotion.servera.application.port.out.CouponIssueRequestSavePort;
import com.promotion.servera.application.port.out.DuplicateIdempotencyKeyException;
import com.promotion.servera.application.port.out.DuplicatePromotionUserException;
import com.promotion.servera.domain.model.CouponIssueRequest;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class CouponIssueRequestPersistenceAdapter implements CouponIssueRequestLoadPort, CouponIssueRequestSavePort {

	private final CouponIssueRequestJpaRepository repository;

	@Override
	public Optional<CouponIssueRequest> findByIdempotencyKey(String idempotencyKey) {
		return repository.findByIdempotencyKey(idempotencyKey)
			.map(CouponIssueRequestEntity::toDomain);
	}

	@Override
	public Optional<CouponIssueRequest> findByPromotionIdAndUserId(Long promotionId, Long userId) {
		return repository.findByPromotionIdAndUserId(promotionId, userId)
			.map(CouponIssueRequestEntity::toDomain);
	}

	@Override
	public void save(CouponIssueRequest request) {
		try {
			repository.saveAndFlush(CouponIssueRequestEntity.from(request));
		} catch (DataIntegrityViolationException exception) {
			throw toDuplicateRequestException(request, exception);
		}
	}

	private RuntimeException toDuplicateRequestException(
		CouponIssueRequest request,
		DataIntegrityViolationException exception
	) {
		String message = exception.getMostSpecificCause().getMessage();
		if (message != null && message.contains("uq_coupon_issue_request_idempotency_key")) {
			return new DuplicateIdempotencyKeyException(request.idempotencyKey());
		}
		if (message != null && message.contains("uq_coupon_issue_request_promotion_user")) {
			return new DuplicatePromotionUserException(request.promotionId(), request.userId());
		}
		return exception;
	}
}
