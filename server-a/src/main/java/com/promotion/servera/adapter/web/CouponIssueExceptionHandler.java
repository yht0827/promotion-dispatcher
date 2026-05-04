package com.promotion.servera.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.promotion.servera.application.port.in.DuplicateCouponIssueRequestException;
import com.promotion.servera.application.port.in.RateLimitExceededException;

@RestControllerAdvice(assignableTypes = CouponIssueController.class)
class CouponIssueExceptionHandler {

	@ExceptionHandler(DuplicateCouponIssueRequestException.class)
	ResponseEntity<Void> handleDuplicateCouponIssueRequest() {
		return ResponseEntity.status(HttpStatus.CONFLICT).build();
	}

	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<Void> handleRateLimitExceeded() {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
	}
}
