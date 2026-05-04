package com.promotion.serverb.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.promotion.common.event.IssueProcessedEvent;
import com.promotion.common.type.IssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueCommand;
import com.promotion.serverb.application.port.in.ProcessCouponIssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueUseCase;
import com.promotion.serverb.application.port.out.CouponStockPort;
import com.promotion.serverb.application.port.out.IssueProcessedPublisherPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ProcessCouponIssueService implements ProcessCouponIssueUseCase {

	private final CouponStockPort couponStockPort;
	private final IssueProcessedPublisherPort issueProcessedPublisherPort;
	private final Clock clock;

	@Override
	public ProcessCouponIssueResult process(ProcessCouponIssueCommand command) {
		// Redis Lua로 재고 차감과 중복 발급 여부를 원자적으로 판단한다.
		IssueResult result = couponStockPort.issue(
			command.requestId(),
			command.promotionId(),
			command.userId()
		);

		// 처리 결과를 issue.processed 이벤트로 바꿔 다음 단계로 전달한다.
		ProcessCouponIssueResult issueResult = ProcessCouponIssueResult.of(command, result);
		IssueProcessedEvent event = IssueProcessedEventFactory.create(issueResult, clock);
		issueProcessedPublisherPort.publish(event);
		return issueResult;
	}
}
