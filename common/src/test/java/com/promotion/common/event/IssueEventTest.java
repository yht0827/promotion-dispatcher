package com.promotion.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.promotion.common.type.IssueResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IssueEventTest {

    @Test
    void issueRequestedEventKeepsMessageContractFields() {
        Instant requestedAt = Instant.parse("2026-05-04T00:00:00Z");

        IssueRequestedEvent event = new IssueRequestedEvent(
                "request-1",
                1L,
                10L,
                "idem-1",
                requestedAt
        );

        assertThat(event.requestId()).isEqualTo("request-1");
        assertThat(event.promotionId()).isEqualTo(1L);
        assertThat(event.userId()).isEqualTo(10L);
        assertThat(event.idempotencyKey()).isEqualTo("idem-1");
        assertThat(event.requestedAt()).isEqualTo(requestedAt);
    }

    @Test
    void issueProcessedEventKeepsMessageContractFields() {
        Instant processedAt = Instant.parse("2026-05-04T00:00:01Z");

        IssueProcessedEvent event = new IssueProcessedEvent(
                "request-1",
                1L,
                10L,
                IssueResult.SOLD_OUT,
                "sold out",
                processedAt
        );

        assertThat(event.requestId()).isEqualTo("request-1");
        assertThat(event.promotionId()).isEqualTo(1L);
        assertThat(event.userId()).isEqualTo(10L);
        assertThat(event.result()).isEqualTo(IssueResult.SOLD_OUT);
        assertThat(event.reason()).isEqualTo("sold out");
        assertThat(event.processedAt()).isEqualTo(processedAt);
    }

    @Test
    void eventRequiresIdentifiersAndPositiveIds() {
        assertThatThrownBy(() -> new IssueRequestedEvent(
                null,
                1L,
                10L,
                "idem-1",
                Instant.now()
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new IssueRequestedEvent(
                "request-1",
                0L,
                10L,
                "idem-1",
                Instant.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
