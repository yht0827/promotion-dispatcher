package com.promotion.servera.adapter.redis;

import java.util.List;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.promotion.servera.application.port.out.RateLimitPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class RedisRateLimitAdapter implements RateLimitPort {

	private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
		"""
			local current = redis.call('INCR', KEYS[1])
			if current == 1 then
			  redis.call('EXPIRE', KEYS[1], ARGV[1])
			end
			if current > tonumber(ARGV[2]) then
			  return 0
			end
			return 1
			""",
		Long.class
	);

	private final StringRedisTemplate redisTemplate;
	private final RateLimitProperties properties;

	@Override
	public boolean isAllowed(Long userId) {
		try {
			Long allowed = redisTemplate.execute(
				RATE_LIMIT_SCRIPT,
				List.of(key(userId)),
				properties.windowSeconds().toString(),
				properties.maxRequests().toString()
			);
			return Long.valueOf(1).equals(allowed);
		} catch (RedisConnectionFailureException exception) {
			// Redis 장애 때문에 요청 접수 API 전체가 막히지 않도록 제한 검사는 fail-open으로 둔다.
			return true;
		}
	}

	private String key(Long userId) {
		return "rate-limit:" + userId;
	}
}
