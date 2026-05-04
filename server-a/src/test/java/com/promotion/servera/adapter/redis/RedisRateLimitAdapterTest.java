package com.promotion.servera.adapter.redis;

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

@Testcontainers
class RedisRateLimitAdapterTest {

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
		.withExposedPorts(6379);

	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisRateLimitAdapter adapter;

	@BeforeEach
	void setUp() {
		connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
		connectionFactory.afterPropertiesSet();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
		adapter = new RedisRateLimitAdapter(redisTemplate, new RateLimitProperties(2, 60));
	}

	@AfterEach
	void tearDown() {
		connectionFactory.destroy();
	}

	@Test
	void isAllowedReturnsFalseAfterUserRequestLimitIsExceeded() {
		assertThat(adapter.isAllowed(100L)).isTrue();
		assertThat(adapter.isAllowed(100L)).isTrue();
		assertThat(adapter.isAllowed(100L)).isFalse();
		assertThat(redisTemplate.getExpire("rate-limit:100")).isPositive();
	}
}
