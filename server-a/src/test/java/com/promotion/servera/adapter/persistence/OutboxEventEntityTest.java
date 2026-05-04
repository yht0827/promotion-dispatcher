package com.promotion.servera.adapter.persistence;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.promotion.servera.domain.model.OutboxEvent;
import com.promotion.servera.domain.model.OutboxEventStatus;

class OutboxEventEntityTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 4, 12, 0);

	@Test
	void markFailedKeepsFailedWhenRetryCountIsBelowMaxRetryCount() {
		OutboxEventEntity entity = OutboxEventEntity.from(pendingEvent("event-1"));

		entity.markFailed("rabbitmq down", NOW, 3);

		assertThat(entity.toDomain().status()).isEqualTo(OutboxEventStatus.FAILED);
	}

	@Test
	void markFailedMarksDeadWhenRetryCountReachesMaxRetryCount() {
		OutboxEventEntity entity = OutboxEventEntity.from(pendingEvent("event-1"));
		entity.markFailed("rabbitmq down", NOW, 3);
		entity.markFailed("rabbitmq down", NOW, 3);

		entity.markFailed("rabbitmq down", NOW, 3);

		assertThat(entity.toDomain().status()).isEqualTo(OutboxEventStatus.DEAD);
	}

	private static OutboxEvent pendingEvent(String eventId) {
		return OutboxEvent.pendingIssueRequested(
			eventId,
			"request-1",
			"{\"requestId\":\"request-1\"}",
			NOW
		);
	}
}
