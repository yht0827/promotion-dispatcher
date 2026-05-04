package com.promotion.serverb.adapter.redis;

import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import com.promotion.common.type.IssueResult;
import com.promotion.serverb.application.port.out.CouponStockPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class RedisCouponStockAdapter implements CouponStockPort {

	private static final DefaultRedisScript<String> ISSUE_SCRIPT = issueScript();

	private final StringRedisTemplate redisTemplate;
	private final CouponStockRedisProperties properties;

	@Override
	public IssueResult issue(String requestId, Long promotionId, Long userId) {
		String result = redisTemplate.execute(
			ISSUE_SCRIPT,
			List.of(resultKey(requestId), issuedUsersKey(promotionId), stockKey(promotionId)),
			userId.toString(),
			properties.resultTtlSeconds().toString(),
			properties.promotionTtlSeconds().toString()
		);
		return toIssueResult(result);
	}

	private static DefaultRedisScript<String> issueScript() {
		DefaultRedisScript<String> script = new DefaultRedisScript<>();
		script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/issue-coupon.lua")));
		script.setResultType(String.class);
		return script;
	}

	private IssueResult toIssueResult(String result) {
		if (result == null) {
			throw new IllegalStateException("Redis 쿠폰 발급 스크립트 결과가 비어 있습니다");
		}
		try {
			return IssueResult.valueOf(result);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("Redis 쿠폰 발급 스크립트 결과가 올바르지 않습니다: " + result, exception);
		}
	}

	private String resultKey(String requestId) {
		return "issue:" + requestId + ":result";
	}

	private String issuedUsersKey(Long promotionId) {
		return "promotion:" + promotionId + ":issued-users";
	}

	private String stockKey(Long promotionId) {
		return "promotion:" + promotionId + ":stock";
	}
}
