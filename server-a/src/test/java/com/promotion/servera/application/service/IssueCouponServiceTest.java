package com.promotion.servera.application.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promotion.servera.application.port.in.DuplicateCouponIssueRequestException;
import com.promotion.servera.application.port.in.IssueCouponCommand;
import com.promotion.servera.application.port.in.IssueCouponResult;
import com.promotion.servera.application.port.in.RateLimitExceededException;
import com.promotion.servera.application.port.out.CouponIssueRequestLoadPort;
import com.promotion.servera.application.port.out.CouponIssueRequestSavePort;
import com.promotion.servera.application.port.out.DuplicateIdempotencyKeyException;
import com.promotion.servera.application.port.out.DuplicatePromotionUserException;
import com.promotion.servera.application.port.out.OutboxEventSavePort;
import com.promotion.servera.application.port.out.RateLimitPort;
import com.promotion.servera.domain.model.CouponIssueRequest;
import com.promotion.servera.domain.model.OutboxEvent;

class IssueCouponServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 4, 12, 0);

	@Test
	void issueReturnsExistingRequestWhenConcurrentInsertConflictsOnIdempotencyKey() {
		FakeCouponIssueRequestLoadPort loadPort = new FakeCouponIssueRequestLoadPort();
		FakeCouponIssueRequestSavePort requestSavePort = new FakeCouponIssueRequestSavePort();
		FakeOutboxEventSavePort outboxEventSavePort = new FakeOutboxEventSavePort();
		IssueCouponService service = new IssueCouponService(
			new FakeRateLimitPort(),
			loadPort,
			requestSavePort,
			outboxEventSavePort,
			new IssueRequestedOutboxEventFactory(new ObjectMapper())
		);
		loadPort.existingByIdempotencyKey = acceptedRequest("existing-request", "idem-1");
		requestSavePort.failure = new DuplicateIdempotencyKeyException("idem-1");

		IssueCouponResult result = service.issue(command("idem-1"));

		assertThat(result).isEqualTo(new IssueCouponResult("existing-request", "ACCEPTED"));
		assertThat(outboxEventSavePort.savedEvents).isEmpty();
	}

	@Test
	void issueThrowsDuplicateRequestWhenConcurrentInsertConflictsOnPromotionUser() {
		FakeCouponIssueRequestLoadPort loadPort = new FakeCouponIssueRequestLoadPort();
		FakeCouponIssueRequestSavePort requestSavePort = new FakeCouponIssueRequestSavePort();
		FakeOutboxEventSavePort outboxEventSavePort = new FakeOutboxEventSavePort();
		IssueCouponService service = new IssueCouponService(
			new FakeRateLimitPort(),
			loadPort,
			requestSavePort,
			outboxEventSavePort,
			new IssueRequestedOutboxEventFactory(new ObjectMapper())
		);
		requestSavePort.failure = new DuplicatePromotionUserException(1L, 100L);

		assertThatThrownBy(() -> service.issue(command("idem-1")))
			.isInstanceOf(DuplicateCouponIssueRequestException.class);
		assertThat(outboxEventSavePort.savedEvents).isEmpty();
	}

	@Test
	void issueRejectsRequestWhenRateLimitExceeded() {
		FakeRateLimitPort rateLimitPort = new FakeRateLimitPort();
		FakeCouponIssueRequestLoadPort loadPort = new FakeCouponIssueRequestLoadPort();
		FakeCouponIssueRequestSavePort requestSavePort = new FakeCouponIssueRequestSavePort();
		FakeOutboxEventSavePort outboxEventSavePort = new FakeOutboxEventSavePort();
		IssueCouponService service = new IssueCouponService(
			rateLimitPort,
			loadPort,
			requestSavePort,
			outboxEventSavePort,
			new IssueRequestedOutboxEventFactory(new ObjectMapper())
		);
		rateLimitPort.allowed = false;

		assertThatThrownBy(() -> service.issue(command("idem-1")))
			.isInstanceOf(RateLimitExceededException.class);
		assertThat(requestSavePort.savedRequests).isEmpty();
		assertThat(outboxEventSavePort.savedEvents).isEmpty();
	}

	private static IssueCouponCommand command(String idempotencyKey) {
		return new IssueCouponCommand(
			1L,
			100L,
			idempotencyKey,
			"{\"requestField10\":\"value10\"}"
		);
	}

	private static CouponIssueRequest acceptedRequest(String requestId, String idempotencyKey) {
		return CouponIssueRequest.accepted(
			requestId,
			1L,
			100L,
			idempotencyKey,
			"{\"requestField10\":\"value10\"}",
			NOW
		);
	}

	private static class FakeCouponIssueRequestLoadPort implements CouponIssueRequestLoadPort {

		private CouponIssueRequest existingByIdempotencyKey;

		@Override
		public Optional<CouponIssueRequest> findByIdempotencyKey(String idempotencyKey) {
			return Optional.ofNullable(existingByIdempotencyKey)
				.filter(request -> request.idempotencyKey().equals(idempotencyKey));
		}

		@Override
		public Optional<CouponIssueRequest> findByPromotionIdAndUserId(Long promotionId, Long userId) {
			return Optional.empty();
		}
	}

	private static class FakeRateLimitPort implements RateLimitPort {

		private boolean allowed = true;

		@Override
		public boolean isAllowed(Long userId) {
			return allowed;
		}
	}

	private static class FakeCouponIssueRequestSavePort implements CouponIssueRequestSavePort {

		private final List<CouponIssueRequest> savedRequests = new ArrayList<>();
		private RuntimeException failure;

		@Override
		public void save(CouponIssueRequest request) {
			if (failure != null) {
				throw failure;
			}
			savedRequests.add(request);
		}
	}

	private static class FakeOutboxEventSavePort implements OutboxEventSavePort {

		private final List<OutboxEvent> savedEvents = new ArrayList<>();

		@Override
		public void save(OutboxEvent event) {
			savedEvents.add(event);
		}
	}
}
