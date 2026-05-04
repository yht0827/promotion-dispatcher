package com.promotion.serverc.application.port.out;

import com.promotion.serverc.domain.model.CouponIssueResult;

public interface CouponIssueResultSavePort {

	void save(CouponIssueResult result);
}
