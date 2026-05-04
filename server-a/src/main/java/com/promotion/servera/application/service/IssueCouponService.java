package com.promotion.servera.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.promotion.servera.application.port.in.DuplicateCouponIssueRequestException;
import com.promotion.servera.application.port.in.IssueCouponCommand;
import com.promotion.servera.application.port.in.IssueCouponResult;
import com.promotion.servera.application.port.in.IssueCouponUseCase;
import com.promotion.servera.application.port.out.CouponIssueRequestLoadPort;
import com.promotion.servera.application.port.out.CouponIssueRequestSavePort;
import com.promotion.servera.application.port.out.OutboxEventSavePort;
import com.promotion.servera.domain.model.CouponIssueRequest;
import com.promotion.servera.domain.model.OutboxEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class IssueCouponService implements IssueCouponUseCase {

	private final CouponIssueRequestLoadPort requestLoadPort;
	private final CouponIssueRequestSavePort requestSavePort;
	private final OutboxEventSavePort outboxEventSavePort;
	private final IssueRequestedOutboxEventFactory outboxEventFactory;
	private final Clock clock = Clock.systemUTC();

	@Override
	@Transactional
	public IssueCouponResult issue(IssueCouponCommand command) {
		return requestLoadPort.findByIdempotencyKey(command.idempotencyKey())
			.map(this::toResult)
			.orElseGet(() -> issueNewRequest(command));
	}

	private IssueCouponResult issueNewRequest(IssueCouponCommand command) {
		requestLoadPort.findByPromotionIdAndUserId(command.promotionId(), command.userId())
			.ifPresent(existing -> {
				throw new DuplicateCouponIssueRequestException(command.promotionId(), command.userId());
			});

		String requestId = UUID.randomUUID().toString();
		LocalDateTime now = LocalDateTime.now(clock);
		Instant requestedAt = Instant.now(clock);

		CouponIssueRequest request = CouponIssueRequest.accepted(
			requestId,
			command.promotionId(),
			command.userId(),
			command.idempotencyKey(),
			command.payloadJson(),
			now
		);
		requestSavePort.save(request);

		OutboxEvent outboxEvent = outboxEventFactory.create(request, requestedAt);
		outboxEventSavePort.save(outboxEvent);

		return toResult(request);
	}

	private IssueCouponResult toResult(CouponIssueRequest request) {
		return new IssueCouponResult(request.requestId(), request.status().name());
	}
}
