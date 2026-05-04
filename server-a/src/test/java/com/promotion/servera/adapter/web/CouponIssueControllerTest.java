package com.promotion.servera.adapter.web;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CouponIssueControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("DELETE FROM outbox_event");
		jdbcTemplate.update("DELETE FROM coupon_issue_request");
	}

	@Test
	void issueCouponAcceptsRequestAndStoresRequestWithOutbox() throws Exception {
		String idempotencyKey = "idem-" + System.nanoTime();
		long userId = System.nanoTime();

		mockMvc.perform(post("/api/v1/promotions/{promotionId}/coupons/issue", 1L)
				.header("Idempotency-Key", idempotencyKey)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.requestId").isNotEmpty())
			.andExpect(jsonPath("$.status").value("ACCEPTED"));

		Integer requestCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupon_issue_request WHERE promotion_id = ? AND user_id = ? AND idempotency_key = ? AND status = ?",
			Integer.class,
			1L,
			userId,
			idempotencyKey,
			"ACCEPTED"
		);
		Integer outboxCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM outbox_event WHERE event_type = ? AND status = ?",
			Integer.class,
			"issue.requested",
			"PENDING"
		);
		String savedRequestField10 = jdbcTemplate.queryForObject(
			"SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.requestField10')) FROM coupon_issue_request WHERE idempotency_key = ?",
			String.class,
			idempotencyKey
		);

		assertThat(requestCount).isEqualTo(1);
		assertThat(outboxCount).isEqualTo(1);
		assertThat(savedRequestField10).isEqualTo("value10");
	}

	@Test
	void issueCouponRejectsRequestWithoutIdempotencyKey() throws Exception {
		mockMvc.perform(post("/api/v1/promotions/{promotionId}/coupons/issue", 1L)
				.header("X-User-Id", 10L)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void issueCouponRejectsRequestWithoutUserId() throws Exception {
		mockMvc.perform(post("/api/v1/promotions/{promotionId}/coupons/issue", 1L)
				.header("Idempotency-Key", "idem-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void issueCouponRejectsRequestWithoutRequiredBodyField() throws Exception {
		mockMvc.perform(post("/api/v1/promotions/{promotionId}/coupons/issue", 1L)
				.header("Idempotency-Key", "idem-1")
				.header("X-User-Id", 10L)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBodyWithoutField10()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void issueCouponReturnsExistingRequestWithoutCreatingOutboxWhenIdempotencyKeyIsRepeated() throws Exception {
		String idempotencyKey = "idem-" + System.nanoTime();
		long userId = System.nanoTime();

		mockMvc.perform(post("/api/v1/promotions/{promotionId}/coupons/issue", 1L)
				.header("Idempotency-Key", idempotencyKey)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody()))
			.andExpect(status().isAccepted());

		String requestId = jdbcTemplate.queryForObject(
			"SELECT request_id FROM coupon_issue_request WHERE idempotency_key = ?",
			String.class,
			idempotencyKey
		);

		mockMvc.perform(post("/api/v1/promotions/{promotionId}/coupons/issue", 1L)
				.header("Idempotency-Key", idempotencyKey)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.requestId").value(requestId))
			.andExpect(jsonPath("$.status").value("ACCEPTED"));

		Integer requestCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupon_issue_request WHERE idempotency_key = ?",
			Integer.class,
			idempotencyKey
		);
		Integer outboxCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?",
			Integer.class,
			requestId
		);

		assertThat(requestCount).isEqualTo(1);
		assertThat(outboxCount).isEqualTo(1);
	}

	private String requestBody() {
		return """
			{
			  "requestField1": "value1",
			  "requestField2": "value2",
			  "requestField3": "value3",
			  "requestField4": "value4",
			  "requestField5": "value5",
			  "requestField6": "value6",
			  "requestField7": "value7",
			  "requestField8": "value8",
			  "requestField9": "value9",
			  "requestField10": "value10"
			}
			""";
	}

	private String requestBodyWithoutField10() {
		return """
			{
			  "requestField1": "value1",
			  "requestField2": "value2",
			  "requestField3": "value3",
			  "requestField4": "value4",
			  "requestField5": "value5",
			  "requestField6": "value6",
			  "requestField7": "value7",
			  "requestField8": "value8",
			  "requestField9": "value9"
			}
			""";
	}
}
