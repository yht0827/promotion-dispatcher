package com.promotion.servera.adapter.persistence;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.promotion.servera.application.port.out.OutboxEventLoadPort;
import com.promotion.servera.application.port.out.OutboxEventSavePort;
import com.promotion.servera.application.port.out.OutboxEventStatusUpdatePort;
import com.promotion.servera.domain.model.OutboxEvent;
import com.promotion.servera.domain.model.OutboxEventStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class OutboxEventPersistenceAdapter implements OutboxEventLoadPort, OutboxEventSavePort, OutboxEventStatusUpdatePort {

	private final OutboxEventJpaRepository repository;

	@Override
	public void save(OutboxEvent event) {
		repository.save(OutboxEventEntity.from(event));
	}

	@Override
	@Transactional(readOnly = true)
	public List<OutboxEvent> findPublishable(int limit) {
		return repository
			.findByStatusInOrderByCreatedAtAsc(
				List.of(OutboxEventStatus.PENDING.name(), OutboxEventStatus.FAILED.name()),
				PageRequest.of(0, limit)
			)
			.stream()
			.map(OutboxEventEntity::toDomain)
			.toList();
	}

	@Override
	@Transactional
	public void markPublished(String eventId, LocalDateTime publishedAt) {
		repository.findByEventId(eventId)
			.ifPresent(event -> event.markPublished(publishedAt));
	}

	@Override
	@Transactional
	public void markFailed(String eventId, String failureMessage, LocalDateTime failedAt, int maxRetryCount) {
		repository.findByEventId(eventId)
			.ifPresent(event -> event.markFailed(failureMessage, failedAt, maxRetryCount));
	}
}
