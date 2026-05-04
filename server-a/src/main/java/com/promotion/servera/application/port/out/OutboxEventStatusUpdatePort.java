package com.promotion.servera.application.port.out;

import java.time.LocalDateTime;

public interface OutboxEventStatusUpdatePort {

	void markPublished(String eventId, LocalDateTime publishedAt);

	void markFailed(String eventId, String failureMessage, LocalDateTime failedAt, int maxRetryCount);
}
