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

import com.promotion.servera.domain.model.OutboxEvent;
import com.promotion.servera.domain.model.OutboxEventStatus;

@Entity
@Table(name = "outbox_event")
@Builder(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class OutboxEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "bigint unsigned")
	private Long id;

	@Column(name = "event_id", nullable = false, columnDefinition = "char(36)", unique = true)
	private String eventId;

	@Column(name = "aggregate_type", nullable = false, length = 50)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false, columnDefinition = "char(36)")
	private String aggregateId;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(name = "payload_json", nullable = false, columnDefinition = "json")
	private String payloadJson;

	@Column(name = "status", nullable = false, length = 30)
	private String status;

	@Column(name = "retry_count", nullable = false, columnDefinition = "int unsigned")
	private Integer retryCount;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	static OutboxEventEntity from(OutboxEvent event) {
		return OutboxEventEntity.builder()
			.eventId(event.eventId())
			.aggregateType(event.aggregateType())
			.aggregateId(event.aggregateId())
			.eventType(event.eventType())
			.payloadJson(event.payloadJson())
			.status(event.status().name())
			.retryCount(0)
			.createdAt(event.createdAt())
			.updatedAt(event.updatedAt())
			.build();
	}

	OutboxEvent toDomain() {
		return new OutboxEvent(
			eventId,
			aggregateType,
			aggregateId,
			eventType,
			payloadJson,
			OutboxEventStatus.valueOf(status),
			createdAt,
			updatedAt
		);
	}

	void markPublished(LocalDateTime publishedAt) {
		this.status = OutboxEventStatus.PUBLISHED.name();
		this.updatedAt = publishedAt;
		this.publishedAt = publishedAt;
	}

	void markFailed(String failureMessage, LocalDateTime failedAt, int maxRetryCount) {
		this.retryCount++;
		this.status = failedStatus(maxRetryCount).name();
		this.lastError = failureMessage;
		this.updatedAt = failedAt;
	}

	private OutboxEventStatus failedStatus(int maxRetryCount) {
		if (retryCount >= maxRetryCount) {
			return OutboxEventStatus.DEAD;
		}
		return OutboxEventStatus.FAILED;
	}
}
