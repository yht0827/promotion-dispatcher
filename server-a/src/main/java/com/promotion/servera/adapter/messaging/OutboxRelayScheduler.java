package com.promotion.servera.adapter.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.promotion.servera.application.port.in.PublishOutboxUseCase;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "promotion.outbox-relay.enabled", havingValue = "true")
class OutboxRelayScheduler {

	private final PublishOutboxUseCase publishOutboxUseCase;

	@Scheduled(
		fixedDelayString = "${promotion.outbox-relay.fixed-delay-ms}",
		initialDelayString = "${promotion.outbox-relay.initial-delay-ms}"
	)
	void publishPending() {
		publishOutboxUseCase.publishPending();
	}
}
