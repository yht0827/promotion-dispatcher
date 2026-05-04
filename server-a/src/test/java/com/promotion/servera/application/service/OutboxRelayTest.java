package com.promotion.servera.application.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.promotion.servera.application.port.out.OutboxEventLoadPort;
import com.promotion.servera.application.port.out.OutboxEventPublishPort;
import com.promotion.servera.application.port.out.OutboxEventStatusUpdatePort;
import com.promotion.servera.domain.model.OutboxEvent;

class OutboxRelayTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 4, 12, 0);
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-04T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void publishPendingPublishesEventAndMarksPublished() {
		OutboxEvent event = pendingEvent("event-1");
		FakeOutboxEventLoadPort loadPort = new FakeOutboxEventLoadPort(List.of(event));
		FakeOutboxEventStatusUpdatePort statusUpdatePort = new FakeOutboxEventStatusUpdatePort();
		FakeOutboxEventPublishPort publishPort = new FakeOutboxEventPublishPort();
		OutboxRelay relay = new OutboxRelay(loadPort, statusUpdatePort, publishPort, CLOCK);

		relay.publishPending();

		assertThat(publishPort.publishedEvents).containsExactly(event);
		assertThat(statusUpdatePort.publishedEventIds).containsExactly("event-1");
		assertThat(statusUpdatePort.publishedAt).containsExactly(NOW);
		assertThat(statusUpdatePort.failedEventIds).isEmpty();
	}

	@Test
	void publishPendingPublishesFailedEventAgain() {
		OutboxEvent event = failedEvent("event-1");
		FakeOutboxEventLoadPort loadPort = new FakeOutboxEventLoadPort(List.of(event));
		FakeOutboxEventStatusUpdatePort statusUpdatePort = new FakeOutboxEventStatusUpdatePort();
		FakeOutboxEventPublishPort publishPort = new FakeOutboxEventPublishPort();
		OutboxRelay relay = new OutboxRelay(loadPort, statusUpdatePort, publishPort, CLOCK);

		relay.publishPending();

		assertThat(publishPort.publishedEvents).containsExactly(event);
		assertThat(statusUpdatePort.publishedEventIds).containsExactly("event-1");
	}

	@Test
	void publishPendingMarksFailedWithMaxRetryCountWhenPublishFails() {
		OutboxEvent event = pendingEvent("event-1");
		FakeOutboxEventLoadPort loadPort = new FakeOutboxEventLoadPort(List.of(event));
		FakeOutboxEventStatusUpdatePort statusUpdatePort = new FakeOutboxEventStatusUpdatePort();
		FakeOutboxEventPublishPort publishPort = new FakeOutboxEventPublishPort();
		publishPort.failure = new RuntimeException("rabbitmq down");
		OutboxRelay relay = new OutboxRelay(loadPort, statusUpdatePort, publishPort, CLOCK);

		relay.publishPending();

		assertThat(publishPort.publishedEvents).containsExactly(event);
		assertThat(statusUpdatePort.publishedEventIds).isEmpty();
		assertThat(statusUpdatePort.failedEventIds).containsExactly("event-1");
		assertThat(statusUpdatePort.failureMessages).containsExactly("rabbitmq down");
		assertThat(statusUpdatePort.failedAt).containsExactly(NOW);
		assertThat(statusUpdatePort.maxRetryCounts).containsExactly(3);
	}

	private static OutboxEvent pendingEvent(String eventId) {
		return OutboxEvent.pendingIssueRequested(
			eventId,
			"request-1",
			"{\"requestId\":\"request-1\"}",
			NOW
		);
	}

	private static OutboxEvent failedEvent(String eventId) {
		return new OutboxEvent(
			eventId,
			"CouponIssueRequest",
			"request-1",
			"issue.requested",
			"{\"requestId\":\"request-1\"}",
			com.promotion.servera.domain.model.OutboxEventStatus.FAILED,
			NOW,
			NOW
		);
	}

	private static class FakeOutboxEventLoadPort implements OutboxEventLoadPort {

		private final List<OutboxEvent> pendingEvents;

		private FakeOutboxEventLoadPort(List<OutboxEvent> pendingEvents) {
			this.pendingEvents = pendingEvents;
		}

		@Override
		public List<OutboxEvent> findPublishable(int limit) {
			return pendingEvents;
		}
	}

	private static class FakeOutboxEventStatusUpdatePort implements OutboxEventStatusUpdatePort {

		private final List<String> publishedEventIds = new ArrayList<>();
		private final List<LocalDateTime> publishedAt = new ArrayList<>();
		private final List<String> failedEventIds = new ArrayList<>();
		private final List<String> failureMessages = new ArrayList<>();
		private final List<LocalDateTime> failedAt = new ArrayList<>();
		private final List<Integer> maxRetryCounts = new ArrayList<>();

		@Override
		public void markPublished(String eventId, LocalDateTime publishedAt) {
			this.publishedEventIds.add(eventId);
			this.publishedAt.add(publishedAt);
		}

		@Override
		public void markFailed(String eventId, String failureMessage, LocalDateTime failedAt, int maxRetryCount) {
			this.failedEventIds.add(eventId);
			this.failureMessages.add(failureMessage);
			this.failedAt.add(failedAt);
			this.maxRetryCounts.add(maxRetryCount);
		}
	}

	private static class FakeOutboxEventPublishPort implements OutboxEventPublishPort {

		private final List<OutboxEvent> publishedEvents = new ArrayList<>();
		private RuntimeException failure;

		@Override
		public void publish(OutboxEvent event) {
			publishedEvents.add(event);
			if (failure != null) {
				throw failure;
			}
		}
	}
}
