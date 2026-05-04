package com.promotion.servera.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.promotion.servera.application.port.in.DuplicateCouponIssueRequestException;

@RestControllerAdvice(assignableTypes = CouponIssueController.class)
class CouponIssueExceptionHandler {

	@ExceptionHandler(DuplicateCouponIssueRequestException.class)
	ResponseEntity<Void> handleDuplicateCouponIssueRequest() {
		return ResponseEntity.status(HttpStatus.CONFLICT).build();
	}
}
