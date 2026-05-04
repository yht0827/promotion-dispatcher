package com.promotion.servera.adapter.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "promotion.rate-limit")
record RateLimitProperties(
	Integer maxRequests,
	Integer windowSeconds
) {
}
