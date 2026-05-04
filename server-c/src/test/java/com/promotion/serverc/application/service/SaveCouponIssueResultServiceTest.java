package com.promotion.serverc.application.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.promotion.common.type.IssueResult;
import com.promotion.serverc.application.port.in.SaveCouponIssueResultCommand;
import com.promotion.serverc.application.port.out.CouponIssueResultSavePort;
import com.promotion.serverc.application.port.out.DuplicateCouponIssueResultException;
import com.promotion.serverc.domain.model.CouponIssueResult;

class SaveCouponIssueResultServiceTest {

	@Test
	void saveStoresCouponIssueResult() {
		FakeCouponIssueResultSavePort savePort = new FakeCouponIssueResultSavePort();
		SaveCouponIssueResultService service = new SaveCouponIssueResultService(savePort);
		SaveCouponIssueResultCommand command = command("request-1");

		service.save(command);

		assertThat(savePort.result).isEqualTo(new CouponIssueResult(
			"request-1",
			1L,
			100L,
			IssueResult.SUCCESS,
			null,
			Instant.parse("2026-05-04T03:00:01Z")
		));
	}

	@Test
	void saveIgnoresDuplicateRequestId() {
		DuplicateCouponIssueResultSavePort savePort = new DuplicateCouponIssueResultSavePort();
		SaveCouponIssueResultService service = new SaveCouponIssueResultService(savePort);

		assertThatCode(() -> service.save(command("request-1")))
			.doesNotThrowAnyException();
	}

	private SaveCouponIssueResultCommand command(String requestId) {
		return new SaveCouponIssueResultCommand(
			requestId,
			1L,
			100L,
			IssueResult.SUCCESS,
			null,
			Instant.parse("2026-05-04T03:00:01Z")
		);
	}

	private static class FakeCouponIssueResultSavePort implements CouponIssueResultSavePort {

		private CouponIssueResult result;

		@Override
		public void save(CouponIssueResult result) {
			this.result = result;
		}
	}

	private static class DuplicateCouponIssueResultSavePort implements CouponIssueResultSavePort {

		@Override
		public void save(CouponIssueResult result) {
			throw new DuplicateCouponIssueResultException(result.requestId());
		}
	}
}
