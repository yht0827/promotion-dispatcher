package com.promotion.serverb.application.port.out;

import com.promotion.common.type.IssueResult;

public interface CouponStockPort {

	IssueResult issue(String requestId, Long promotionId, Long userId);
}
