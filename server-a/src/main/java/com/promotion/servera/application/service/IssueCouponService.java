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
import com.promotion.servera.application.port.out.DuplicateIdempotencyKeyException;
import com.promotion.servera.application.port.out.DuplicatePromotionUserException;
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
		// 같은 Idempotency-Key 요청이면 기존 접수 결과를 그대로 반환한다.
		return requestLoadPort.findByIdempotencyKey(command.idempotencyKey())
			.map(this::toResult)
			.orElseGet(() -> issueNewRequest(command));
	}

	private IssueCouponResult issueNewRequest(IssueCouponCommand command) {
		// 같은 프로모션을 이미 신청한 사용자인지 먼저 확인한다.
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
		// 요청 접수 이력을 저장한다. 동시 insert 충돌은 DB unique key를 기준으로 한 번 더 정리한다.
		try {
			requestSavePort.save(request);
		} catch (DuplicateIdempotencyKeyException exception) {
			return requestLoadPort.findByIdempotencyKey(command.idempotencyKey())
				.map(this::toResult)
				.orElseThrow(() -> exception);
		} catch (DuplicatePromotionUserException exception) {
			throw new DuplicateCouponIssueRequestException(command.promotionId(), command.userId());
		}

		// 같은 트랜잭션에서 outbox 이벤트까지 저장해 메시지 유실을 막는다.
		OutboxEvent outboxEvent = outboxEventFactory.create(request, requestedAt);
		outboxEventSavePort.save(outboxEvent);

		return toResult(request);
	}

	private IssueCouponResult toResult(CouponIssueRequest request) {
		return new IssueCouponResult(request.requestId(), request.status().name());
	}
}
