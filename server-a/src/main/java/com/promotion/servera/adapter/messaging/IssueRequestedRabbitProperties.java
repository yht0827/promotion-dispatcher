package com.promotion.servera.adapter.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "promotion.rabbitmq.issue-requested")
record IssueRequestedRabbitProperties(
	String exchange,
	String routingKey
) {
}
