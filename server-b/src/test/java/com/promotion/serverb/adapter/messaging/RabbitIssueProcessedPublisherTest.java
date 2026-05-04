package com.promotion.serverb.adapter.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.promotion.common.event.IssueProcessedEvent;
import com.promotion.common.type.IssueResult;

class RabbitIssueProcessedPublisherTest {

	@Test
	void publishSendsIssueProcessedEventJsonToRabbitMq() throws Exception {
		RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
		ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		IssueProcessedRabbitProperties properties = new IssueProcessedRabbitProperties(
			"issue.processed.exchange",
			"issue.processed"
		);
		RabbitIssueProcessedPublisher publisher = new RabbitIssueProcessedPublisher(
			rabbitTemplate,
			objectMapper,
			properties
		);
		IssueProcessedEvent event = new IssueProcessedEvent(
			"request-1",
			1L,
			100L,
			IssueResult.SUCCESS,
			null,
			Instant.parse("2026-05-04T03:00:01Z")
		);

		publisher.publish(event);

		ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
		verify(rabbitTemplate).convertAndSend(
			eq("issue.processed.exchange"),
			eq("issue.processed"),
			payloadCaptor.capture(),
			processorCaptor.capture()
		);
		JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
		assertThat(payload.get("requestId").asText()).isEqualTo("request-1");
		assertThat(payload.get("promotionId").asLong()).isEqualTo(1L);
		assertThat(payload.get("userId").asLong()).isEqualTo(100L);
		assertThat(payload.get("result").asText()).isEqualTo("SUCCESS");
		assertThat(payload.get("reason").isNull()).isTrue();
		assertThat(payload.get("processedAt").asText()).isEqualTo("2026-05-04T03:00:01Z");

		Message message = MessageBuilder
			.withBody(payloadCaptor.getValue().getBytes(StandardCharsets.UTF_8))
			.build();
		Message processed = processorCaptor.getValue().postProcessMessage(message);
		MessageProperties messageProperties = processed.getMessageProperties();
		assertThat(messageProperties.getMessageId()).isEqualTo("request-1");
		assertThat(messageProperties.getType()).isEqualTo("issue.processed");
		assertThat(messageProperties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
	}
}
