package com.promotion.servera.application.port.out;

import java.util.Optional;

import com.promotion.servera.domain.model.CouponIssueRequest;

public interface CouponIssueRequestLoadPort {

	Optional<CouponIssueRequest> findByIdempotencyKey(String idempotencyKey);
}
