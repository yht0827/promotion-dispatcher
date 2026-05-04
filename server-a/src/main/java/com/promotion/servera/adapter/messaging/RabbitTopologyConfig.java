package com.promotion.servera.adapter.messaging;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RabbitTopologyConfig {

	@Bean
	DirectExchange issueRequestedExchange(IssueRequestedRabbitProperties properties) {
		return new DirectExchange(properties.exchange(), true, false);
	}
}
