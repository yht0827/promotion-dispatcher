package com.promotion.common.event;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.promotion.common.type.IssueResult;

class IssueEventTest {

	@Test
	void issueRequestedEventKeepsMessageContractFields() {
		Instant requestedAt = Instant.parse("2026-05-04T00:00:00Z");

		IssueRequestedEvent event = new IssueRequestedEvent(
			"request-1",
			1L,
			10L,
			"idem-1",
			requestedAt
		);

		assertThat(event.requestId()).isEqualTo("request-1");
		assertThat(event.promotionId()).isEqualTo(1L);
		assertThat(event.userId()).isEqualTo(10L);
		assertThat(event.idempotencyKey()).isEqualTo("idem-1");
		assertThat(event.requestedAt()).isEqualTo(requestedAt);
	}

	@Test
	void issueProcessedEventKeepsMessageContractFields() {
		Instant processedAt = Instant.parse("2026-05-04T00:00:01Z");

		IssueProcessedEvent event = new IssueProcessedEvent(
			"request-1",
			1L,
			10L,
			IssueResult.SOLD_OUT,
			"재고 없음",
			processedAt
		);

		assertThat(event.requestId()).isEqualTo("request-1");
		assertThat(event.promotionId()).isEqualTo(1L);
		assertThat(event.userId()).isEqualTo(10L);
		assertThat(event.result()).isEqualTo(IssueResult.SOLD_OUT);
		assertThat(event.reason()).isEqualTo("재고 없음");
		assertThat(event.processedAt()).isEqualTo(processedAt);
	}

	@Test
	void eventRequiresIdentifiersAndPositiveIds() {
		assertThatThrownBy(() -> new IssueRequestedEvent(
			null,
			1L,
			10L,
			"idem-1",
			Instant.now()
		)).isInstanceOf(NullPointerException.class)
			.hasMessage("요청 ID");

		assertThatThrownBy(() -> new IssueRequestedEvent(
			"request-1",
			0L,
			10L,
			"idem-1",
			Instant.now()
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("프로모션 ID는 0보다 커야 합니다");

		assertThatThrownBy(() -> new IssueRequestedEvent(
			"request-1",
			1L,
			10L,
			" ",
			Instant.now()
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("멱등성 키는 비어 있을 수 없습니다");
	}
}
