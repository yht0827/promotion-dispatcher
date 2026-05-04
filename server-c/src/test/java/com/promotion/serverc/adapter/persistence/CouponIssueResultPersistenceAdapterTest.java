package com.promotion.serverc.adapter.persistence;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.promotion.common.type.IssueResult;
import com.promotion.serverc.application.port.out.DuplicateCouponIssueResultException;
import com.promotion.serverc.domain.model.CouponIssueResult;

@Testcontainers
@DataJpaTest(properties = {
	"spring.flyway.enabled=true",
	"spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CouponIssueResultPersistenceAdapter.class)
class CouponIssueResultPersistenceAdapterTest {

	@Container
	private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
		.withDatabaseName("server_c_db")
		.withUsername("promotion")
		.withPassword("promotion");

	@Autowired
	private CouponIssueResultPersistenceAdapter adapter;

	@DynamicPropertySource
	static void registerDataSource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
	}

	@Test
	void saveThrowsDuplicateResultWhenRequestIdAlreadyExists() {
		adapter.save(result("request-1", 1L, 100L));

		assertThatThrownBy(() -> adapter.save(result("request-1", 1L, 101L)))
			.isInstanceOf(DuplicateCouponIssueResultException.class);
	}

	@Test
	void saveThrowsDuplicateResultWhenPromotionUserAlreadyExists() {
		adapter.save(result("request-1", 1L, 100L));

		assertThatThrownBy(() -> adapter.save(result("request-2", 1L, 100L)))
			.isInstanceOf(DuplicateCouponIssueResultException.class);
	}

	private static CouponIssueResult result(String requestId, Long promotionId, Long userId) {
		return new CouponIssueResult(
			requestId,
			promotionId,
			userId,
			IssueResult.SUCCESS,
			null,
			Instant.parse("2026-05-04T03:00:01Z")
		);
	}
}
