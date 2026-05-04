package com.promotion.serverb.adapter.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "promotion.rabbitmq.issue-processed")
record IssueProcessedRabbitProperties(
	String exchange,
	String routingKey
) {
}
