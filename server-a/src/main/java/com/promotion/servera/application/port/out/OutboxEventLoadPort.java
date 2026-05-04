package com.promotion.servera.application.port.out;

import java.util.List;

import com.promotion.servera.domain.model.OutboxEvent;

public interface OutboxEventLoadPort {

	List<OutboxEvent> findPublishable(int limit);
}
