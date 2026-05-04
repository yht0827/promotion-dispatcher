package com.promotion.serverb.application.service;

import org.springframework.stereotype.Service;

import com.promotion.common.type.IssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueCommand;
import com.promotion.serverb.application.port.in.ProcessCouponIssueResult;
import com.promotion.serverb.application.port.in.ProcessCouponIssueUseCase;
import com.promotion.serverb.application.port.out.CouponStockPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ProcessCouponIssueService implements ProcessCouponIssueUseCase {

	private final CouponStockPort couponStockPort;

	@Override
	public ProcessCouponIssueResult process(ProcessCouponIssueCommand command) {
		IssueResult result = couponStockPort.issue(
			command.requestId(),
			command.promotionId(),
			command.userId()
		);
		return ProcessCouponIssueResult.of(command, result);
	}
}
