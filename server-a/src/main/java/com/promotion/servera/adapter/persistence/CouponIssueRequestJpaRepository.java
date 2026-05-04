package com.promotion.servera.adapter.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestEntity, Long> {

	Optional<CouponIssueRequestEntity> findByIdempotencyKey(String idempotencyKey);
}
