package com.promotion.serverb.application.service;

import org.springframework.stereotype.Service;

import com.promotion.serverb.application.port.in.ProcessCouponIssueCommand;
import com.promotion.serverb.application.port.in.ProcessCouponIssueUseCase;

@Service
class ProcessCouponIssueService implements ProcessCouponIssueUseCase {

	@Override
	public void process(ProcessCouponIssueCommand command) {
		// Redis Lua processing is implemented in the next step.
	}
}
