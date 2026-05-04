package com.promotion.serverb.adapter.redis;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.promotion.common.type.IssueResult;

@Testcontainers
class RedisCouponStockAdapterTest {

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
		.withExposedPorts(6379);

	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisCouponStockAdapter adapter;

	@BeforeEach
	void setUp() {
		connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
		connectionFactory.afterPropertiesSet();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
		adapter = new RedisCouponStockAdapter(redisTemplate, properties());
	}

	@AfterEach
	void tearDown() {
		connectionFactory.destroy();
	}

	@Test
	void issueDecreasesStockAndStoresRequestResultAtomically() {
		redisTemplate.opsForValue().set("promotion:1:stock", "1");

		IssueResult result = adapter.issue("request-1", 1L, 100L);
		IssueResult repeatedResult = adapter.issue("request-1", 1L, 100L);

		assertThat(result).isEqualTo(IssueResult.SUCCESS);
		assertThat(repeatedResult).isEqualTo(IssueResult.SUCCESS);
		assertThat(redisTemplate.opsForValue().get("promotion:1:stock")).isEqualTo("0");
		assertThat(redisTemplate.opsForSet().isMember("promotion:1:issued-users", "100")).isTrue();
		assertThat(redisTemplate.opsForValue().get("issue:request-1:result")).isEqualTo("SUCCESS");
		assertThat(ttl("issue:request-1:result")).isPositive();
		assertThat(ttl("promotion:1:issued-users")).isPositive();
		assertThat(ttl("promotion:1:stock")).isPositive();
	}

	@Test
	void issueReturnsDuplicateWhenUserAlreadyIssued() {
		redisTemplate.opsForValue().set("promotion:1:stock", "2");
		adapter.issue("request-1", 1L, 100L);

		IssueResult result = adapter.issue("request-2", 1L, 100L);

		assertThat(result).isEqualTo(IssueResult.DUPLICATE);
		assertThat(redisTemplate.opsForValue().get("promotion:1:stock")).isEqualTo("1");
		assertThat(redisTemplate.opsForValue().get("issue:request-2:result")).isEqualTo("DUPLICATE");
		assertThat(ttl("issue:request-2:result")).isPositive();
		assertThat(ttl("promotion:1:issued-users")).isPositive();
		assertThat(ttl("promotion:1:stock")).isPositive();
	}

	@Test
	void issueReturnsSoldOutWhenStockIsEmpty() {
		redisTemplate.opsForValue().set("promotion:1:stock", "0");

		IssueResult result = adapter.issue("request-1", 1L, 100L);

		assertThat(result).isEqualTo(IssueResult.SOLD_OUT);
		assertThat(redisTemplate.opsForValue().get("promotion:1:stock")).isEqualTo("0");
		assertThat(redisTemplate.opsForValue().get("issue:request-1:result")).isEqualTo("SOLD_OUT");
		assertThat(ttl("issue:request-1:result")).isPositive();
		assertThat(ttl("promotion:1:stock")).isPositive();
	}

	private Long ttl(String key) {
		return redisTemplate.getExpire(key);
	}

	private static CouponStockRedisProperties properties() {
		return new CouponStockRedisProperties(86400, 604800);
	}
}
