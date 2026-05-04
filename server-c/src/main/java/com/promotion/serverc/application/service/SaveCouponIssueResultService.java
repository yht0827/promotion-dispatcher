package com.promotion.serverc.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.promotion.serverc.application.port.in.SaveCouponIssueResultCommand;
import com.promotion.serverc.application.port.in.SaveCouponIssueResultUseCase;
import com.promotion.serverc.application.port.out.CouponIssueResultSavePort;
import com.promotion.serverc.application.port.out.DuplicateCouponIssueResultException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class SaveCouponIssueResultService implements SaveCouponIssueResultUseCase {

	private final CouponIssueResultSavePort savePort;

	@Override
	@Transactional
	public void save(SaveCouponIssueResultCommand command) {
		try {
			savePort.save(command.toDomain());
		} catch (DuplicateCouponIssueResultException ignored) {
			// RabbitMQ 재전달로 이미 저장된 결과면 성공 처리하고 ack할 수 있게 무시한다.
		}
	}
}
