package com.promotion.serverb.application.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.promotion.common.event.IssueProcessedEvent;
import com.promotion.common.type.IssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueResult;

class IssueProcessedEventFactoryTest {

	@Test
	void createBuildsIssueProcessedEventWithReason() {
		Clock clock = Clock.fixed(Instant.parse("2026-05-04T03:00:01Z"), ZoneOffset.UTC);
		ProcessCouponIssueResult result = new ProcessCouponIssueResult(
			"request-1",
			1L,
			100L,
			IssueResult.SOLD_OUT
		);

		IssueProcessedEvent event = IssueProcessedEventFactory.create(result, clock);

		assertThat(event).isEqualTo(new IssueProcessedEvent(
			"request-1",
			1L,
			100L,
			IssueResult.SOLD_OUT,
			"쿠폰 재고가 소진되었습니다",
			Instant.parse("2026-05-04T03:00:01Z")
		));
	}
}
