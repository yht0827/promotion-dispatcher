package com.promotion.servera.adapter.web;

import jakarta.validation.constraints.NotBlank;

record IssueCouponRequest(
	@NotBlank String requestField1,
	@NotBlank String requestField2,
	@NotBlank String requestField3,
	@NotBlank String requestField4,
	@NotBlank String requestField5,
	@NotBlank String requestField6,
	@NotBlank String requestField7,
	@NotBlank String requestField8,
	@NotBlank String requestField9,
	@NotBlank String requestField10
) {
}
