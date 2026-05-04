package com.promotion.serverb.adapter.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "promotion.rabbitmq.issue-requested")
record IssueRequestedRabbitProperties(
	String exchange,
	String queue,
	String routingKey,
	String deadLetterExchange,
	String deadLetterQueue,
	String deadLetterRoutingKey
) {
}
