package com.promotion.serverb.application.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.promotion.common.event.IssueProcessedEvent;
import com.promotion.common.type.IssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueCommand;
import com.promotion.serverb.application.port.in.ProcessCouponIssueResult;
import com.promotion.serverb.application.port.out.CouponStockPort;
import com.promotion.serverb.application.port.out.IssueProcessedPublisherPort;

class ProcessCouponIssueServiceTest {

	@Test
	void processIssuesCouponThroughCouponStockPort() {
		FakeCouponStockPort couponStockPort = new FakeCouponStockPort(IssueResult.SUCCESS);
		FakeIssueProcessedPublisherPort publisherPort = new FakeIssueProcessedPublisherPort();
		Clock clock = Clock.fixed(Instant.parse("2026-05-04T03:00:01Z"), ZoneOffset.UTC);
		ProcessCouponIssueService service = new ProcessCouponIssueService(couponStockPort, publisherPort, clock);
		ProcessCouponIssueCommand command = new ProcessCouponIssueCommand(
			"request-1",
			1L,
			100L,
			"idem-1",
			Instant.parse("2026-05-04T03:00:00Z")
		);

		ProcessCouponIssueResult result = service.process(command);

		assertThat(result.result()).isEqualTo(IssueResult.SUCCESS);
		assertThat(couponStockPort.requestId).isEqualTo("request-1");
		assertThat(couponStockPort.promotionId).isEqualTo(1L);
		assertThat(couponStockPort.userId).isEqualTo(100L);
		assertThat(publisherPort.event).isEqualTo(new IssueProcessedEvent(
			"request-1",
			1L,
			100L,
			IssueResult.SUCCESS,
			null,
			Instant.parse("2026-05-04T03:00:01Z")
		));
	}

	private static class FakeCouponStockPort implements CouponStockPort {

		private final IssueResult result;
		private String requestId;
		private Long promotionId;
		private Long userId;

		private FakeCouponStockPort(IssueResult result) {
			this.result = result;
		}

		@Override
		public IssueResult issue(String requestId, Long promotionId, Long userId) {
			this.requestId = requestId;
			this.promotionId = promotionId;
			this.userId = userId;
			return result;
		}
	}

	private static class FakeIssueProcessedPublisherPort implements IssueProcessedPublisherPort {

		private IssueProcessedEvent event;

		@Override
		public void publish(IssueProcessedEvent event) {
			this.event = event;
		}
	}
}
