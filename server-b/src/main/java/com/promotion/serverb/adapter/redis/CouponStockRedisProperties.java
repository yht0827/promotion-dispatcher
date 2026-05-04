package com.promotion.serverb.adapter.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "promotion.redis.coupon-stock")
record CouponStockRedisProperties(
	Integer resultTtlSeconds,
	Integer promotionTtlSeconds
) {
}
