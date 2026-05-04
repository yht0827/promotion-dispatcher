package com.promotion.servera.application.port.out;

import com.promotion.servera.domain.model.OutboxEvent;

public interface OutboxEventPublishPort {

	void publish(OutboxEvent event);
}
