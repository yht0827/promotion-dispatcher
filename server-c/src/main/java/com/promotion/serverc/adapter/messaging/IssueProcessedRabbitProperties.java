package com.promotion.serverc.adapter.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "promotion.rabbitmq.issue-processed")
record IssueProcessedRabbitProperties(
	String exchange,
	String queue,
	String routingKey,
	String deadLetterExchange,
	String deadLetterQueue,
	String deadLetterRoutingKey
) {
}
