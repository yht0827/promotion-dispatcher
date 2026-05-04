package com.promotion.serverb.application.port.in;

public interface ProcessCouponIssueUseCase {

	ProcessCouponIssueResult process(ProcessCouponIssueCommand command);
}
