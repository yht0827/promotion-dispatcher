package com.promotion.servera.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promotion.servera.application.port.in.RateLimitExceededException;

class CouponIssueExceptionHandlerTest {

	@Test
	void rateLimitExceededReturnsTooManyRequests() throws Exception {
		CouponIssueController controller = new CouponIssueController(
			command -> {
				throw new RateLimitExceededException(command.userId());
			},
			new IssueCouponRequestMapper(new ObjectMapper())
		);
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
			.setControllerAdvice(new CouponIssueExceptionHandler())
			.build();

		mockMvc.perform(post("/api/v1/promotions/{promotionId}/coupons/issue", 1L)
				.header("Idempotency-Key", "idem-1")
				.header("X-User-Id", 100L)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody()))
			.andExpect(status().isTooManyRequests());
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
}
