package com.promotion.servera.adapter.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.promotion.servera.application.port.out.CouponIssueRequestLoadPort;
import com.promotion.servera.application.port.out.CouponIssueRequestSavePort;
import com.promotion.servera.application.port.out.OutboxEventSavePort;
import com.promotion.servera.domain.model.CouponIssueRequest;
import com.promotion.servera.domain.model.OutboxEvent;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class CouponIssuePersistenceAdapter implements CouponIssueRequestLoadPort, CouponIssueRequestSavePort, OutboxEventSavePort {

	private final CouponIssueRequestJpaRepository requestRepository;
	private final OutboxEventJpaRepository outboxEventRepository;

	@Override
	public Optional<CouponIssueRequest> findByIdempotencyKey(String idempotencyKey) {
		return requestRepository.findByIdempotencyKey(idempotencyKey)
			.map(CouponIssueRequestEntity::toDomain);
	}

	@Override
	public Optional<CouponIssueRequest> findByPromotionIdAndUserId(Long promotionId, Long userId) {
		return requestRepository.findByPromotionIdAndUserId(promotionId, userId)
			.map(CouponIssueRequestEntity::toDomain);
	}

	@Override
	public void save(CouponIssueRequest request) {
		requestRepository.save(CouponIssueRequestEntity.from(request));
	}

	@Override
	public void save(OutboxEvent event) {
		outboxEventRepository.save(OutboxEventEntity.from(event));
	}
}
