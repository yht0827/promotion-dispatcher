package com.promotion.common.event;

import com.promotion.common.type.IssueResult;
import java.time.Instant;
import java.util.Objects;

public record IssueProcessedEvent(
        String requestId,
        Long promotionId,
        Long userId,
        IssueResult result,
        String reason,
        Instant processedAt
) {

    public IssueProcessedEvent {
        requireText(requestId, "requestId");
        requirePositive(promotionId, "promotionId");
        requirePositive(userId, "userId");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(processedAt, "processedAt");
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
