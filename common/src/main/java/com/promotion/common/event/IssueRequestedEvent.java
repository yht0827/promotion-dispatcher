package com.promotion.common.event;

import java.time.Instant;
import java.util.Objects;

public record IssueRequestedEvent(
        String requestId,
        Long promotionId,
        Long userId,
        String idempotencyKey,
        Instant requestedAt
) {

    public IssueRequestedEvent {
        requireText(requestId, "requestId");
        requirePositive(promotionId, "promotionId");
        requirePositive(userId, "userId");
        requireText(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    private static void requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static void requirePositive(Long value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
