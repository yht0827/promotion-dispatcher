CREATE TABLE coupon_issue_result (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id CHAR(36) NOT NULL,
    promotion_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    result VARCHAR(30) NOT NULL,
    reason VARCHAR(255) NULL,
    processed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_coupon_issue_result_request_id (request_id),
    UNIQUE KEY uq_coupon_issue_result_promotion_user (promotion_id, user_id),
    KEY ix_coupon_issue_result_result_created_at (result, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
