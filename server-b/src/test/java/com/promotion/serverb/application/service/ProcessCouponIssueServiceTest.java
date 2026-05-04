package com.promotion.serverb.application.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.promotion.common.type.IssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueCommand;
import com.promotion.serverb.application.port.in.ProcessCouponIssueResult;
import com.promotion.serverb.application.port.out.CouponStockPort;

class ProcessCouponIssueServiceTest {

	@Test
	void processIssuesCouponThroughCouponStockPort() {
		FakeCouponStockPort couponStockPort = new FakeCouponStockPort(IssueResult.SUCCESS);
		ProcessCouponIssueService service = new ProcessCouponIssueService(couponStockPort);
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
}
