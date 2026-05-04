package com.promotion.servera.application.port.out;

import com.promotion.servera.domain.model.CouponIssueRequest;

public interface CouponIssueRequestSavePort {

	void save(CouponIssueRequest request);
}
