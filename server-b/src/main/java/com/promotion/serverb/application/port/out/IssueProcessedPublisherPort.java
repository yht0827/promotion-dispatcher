package com.promotion.serverb.application.port.out;

import com.promotion.common.event.IssueProcessedEvent;

public interface IssueProcessedPublisherPort {

	void publish(IssueProcessedEvent event);
}
