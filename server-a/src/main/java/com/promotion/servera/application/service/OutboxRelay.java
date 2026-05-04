package com.promotion.servera.application.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.promotion.servera.application.port.in.PublishOutboxUseCase;
import com.promotion.servera.application.port.out.OutboxEventLoadPort;
import com.promotion.servera.application.port.out.OutboxEventPublishPort;
import com.promotion.servera.application.port.out.OutboxEventStatusUpdatePort;
import com.promotion.servera.domain.model.OutboxEvent;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class OutboxRelay implements PublishOutboxUseCase {

	private static final int PUBLISH_BATCH_SIZE = 100;
	private static final int MAX_RETRY_COUNT = 3;

	private final OutboxEventLoadPort loadPort;
	private final OutboxEventStatusUpdatePort statusUpdatePort;
	private final OutboxEventPublishPort publishPort;
	private final Clock clock;

	@Override
	public void publishPending() {
		loadPort.findPublishable(PUBLISH_BATCH_SIZE)
			.forEach(this::publish);
	}

	private void publish(OutboxEvent event) {
		LocalDateTime now = LocalDateTime.now(clock);
		try {
			publishPort.publish(event);
			statusUpdatePort.markPublished(event.eventId(), now);
		} catch (RuntimeException exception) {
			statusUpdatePort.markFailed(event.eventId(), exception.getMessage(), now, MAX_RETRY_COUNT);
		}
	}
}
