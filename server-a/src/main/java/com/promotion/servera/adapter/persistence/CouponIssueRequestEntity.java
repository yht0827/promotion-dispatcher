package com.promotion.servera.adapter.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import com.promotion.servera.domain.model.CouponIssueRequest;
import com.promotion.servera.domain.model.CouponIssueRequestStatus;

@Entity
@Table(name = "coupon_issue_request")
@Builder(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class CouponIssueRequestEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "bigint unsigned")
	private Long id;

	@Column(name = "request_id", nullable = false, columnDefinition = "char(36)", unique = true)
	private String requestId;

	@Column(name = "promotion_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long promotionId;

	@Column(name = "user_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long userId;

	@Column(name = "idempotency_key", nullable = false, length = 100, unique = true)
	private String idempotencyKey;

	@Column(name = "status", nullable = false, length = 30)
	private String status;

	@Column(name = "payload_json", nullable = false, columnDefinition = "json")
	private String payloadJson;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	static CouponIssueRequestEntity from(CouponIssueRequest request) {
		return CouponIssueRequestEntity.builder()
			.requestId(request.requestId())
			.promotionId(request.promotionId())
			.userId(request.userId())
			.idempotencyKey(request.idempotencyKey())
			.status(request.status().name())
			.payloadJson(request.payloadJson())
			.createdAt(request.createdAt())
			.updatedAt(request.updatedAt())
			.build();
	}

	CouponIssueRequest toDomain() {
		return new CouponIssueRequest(
			requestId,
			promotionId,
			userId,
			idempotencyKey,
			CouponIssueRequestStatus.valueOf(status),
			payloadJson,
			createdAt,
			updatedAt
		);
	}
}
