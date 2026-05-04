package com.promotion.serverc.adapter.persistence;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.promotion.common.type.IssueResult;
import com.promotion.serverc.domain.model.CouponIssueResult;

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

@Entity
@Table(name = "coupon_issue_result")
@Builder(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class CouponIssueResultEntity {

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

	@Column(name = "result", nullable = false, length = 30)
	private String result;

	@Column(name = "reason", length = 255)
	private String reason;

	@Column(name = "processed_at", nullable = false)
	private LocalDateTime processedAt;

	static CouponIssueResultEntity from(CouponIssueResult result) {
		return CouponIssueResultEntity.builder()
			.requestId(result.requestId())
			.promotionId(result.promotionId())
			.userId(result.userId())
			.result(result.result().name())
			.reason(result.reason())
			.processedAt(LocalDateTime.ofInstant(result.processedAt(), ZoneOffset.UTC))
			.build();
	}

	CouponIssueResult toDomain() {
		return new CouponIssueResult(
			requestId,
			promotionId,
			userId,
			IssueResult.valueOf(result),
			reason,
			processedAt.toInstant(ZoneOffset.UTC)
		);
	}
}
