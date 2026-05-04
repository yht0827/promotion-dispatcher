package com.promotion.serverb.adapter.messaging;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.promotion.common.type.IssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueCommand;
import com.promotion.serverb.application.port.in.ProcessCouponIssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueUseCase;

class IssueRequestedListenerTest {

	@Test
	void handleIssueRequestedMessageCallsUseCase() throws Exception {
		FakeProcessCouponIssueUseCase useCase = new FakeProcessCouponIssueUseCase();
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		IssueRequestedListener listener = new IssueRequestedListener(objectMapper, useCase);
		String payload = """
			{
			  "requestId": "request-1",
			  "promotionId": 1,
			  "userId": 100,
			  "idempotencyKey": "idem-1",
			  "requestedAt": "2026-05-04T03:00:00Z"
			}
			""";

		listener.handle(payload);

		assertThat(useCase.command).isEqualTo(new ProcessCouponIssueCommand(
			"request-1",
			1L,
			100L,
			"idem-1",
			Instant.parse("2026-05-04T03:00:00Z")
		));
	}

	private static class FakeProcessCouponIssueUseCase implements ProcessCouponIssueUseCase {

		private ProcessCouponIssueCommand command;

		@Override
		public ProcessCouponIssueResult process(ProcessCouponIssueCommand command) {
			this.command = command;
			return new ProcessCouponIssueResult(
				command.requestId(),
				command.promotionId(),
				command.userId(),
				IssueResult.SUCCESS
			);
		}
	}
}
