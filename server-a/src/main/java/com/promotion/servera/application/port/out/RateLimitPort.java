package com.promotion.servera.application.port.out;

public interface RateLimitPort {

	boolean isAllowed(Long userId);
}
