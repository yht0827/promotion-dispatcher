package com.promotion.servera.adapter.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.promotion.servera.application.port.out.CouponIssueRequestLoadPort;
import com.promotion.servera.application.port.out.CouponIssueRequestSavePort;
import com.promotion.servera.application.port.out.OutboxEventLoadPort;
import com.promotion.servera.application.port.out.OutboxEventSavePort;
import com.promotion.servera.application.port.out.OutboxEventStatusUpdatePort;
import com.promotion.servera.domain.model.CouponIssueRequest;
import com.promotion.servera.domain.model.OutboxEvent;
import com.promotion.servera.domain.model.OutboxEventStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class CouponIssuePersistenceAdapter implements CouponIssueRequestLoadPort, CouponIssueRequestSavePort,
	OutboxEventLoadPort, OutboxEventSavePort, OutboxEventStatusUpdatePort {

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

	@Override
	@Transactional(readOnly = true)
	public List<OutboxEvent> findPending(int limit) {
		return outboxEventRepository
			.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING.name(), PageRequest.of(0, limit))
			.stream()
			.map(OutboxEventEntity::toDomain)
			.toList();
	}

	@Override
	@Transactional
	public void markPublished(String eventId, LocalDateTime publishedAt) {
		outboxEventRepository.findByEventId(eventId)
			.ifPresent(event -> event.markPublished(publishedAt));
	}

	@Override
	@Transactional
	public void markFailed(String eventId, String failureMessage, LocalDateTime failedAt) {
		outboxEventRepository.findByEventId(eventId)
			.ifPresent(event -> event.markFailed(failureMessage, failedAt));
	}
}
