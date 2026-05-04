package com.promotion.serverb.adapter.messaging;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class IssueProcessedTopologyConfig {

	@Bean
	DirectExchange issueProcessedExchange(IssueProcessedRabbitProperties properties) {
		return new DirectExchange(properties.exchange(), true, false);
	}
}
