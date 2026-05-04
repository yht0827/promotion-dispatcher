package com.promotion.serverc.adapter.persistence;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.promotion.serverc.application.port.out.CouponIssueResultSavePort;
import com.promotion.serverc.application.port.out.DuplicateCouponIssueResultException;
import com.promotion.serverc.domain.model.CouponIssueResult;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class CouponIssueResultPersistenceAdapter implements CouponIssueResultSavePort {

	private final CouponIssueResultJpaRepository repository;

	@Override
	public void save(CouponIssueResult result) {
		try {
			repository.saveAndFlush(CouponIssueResultEntity.from(result));
		} catch (DataIntegrityViolationException exception) {
			throw new DuplicateCouponIssueResultException(result.requestId());
		}
	}
}
