package com.promotion.servera;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.test.context.SpringBootTest;

import com.zaxxer.hikari.HikariDataSource;

@SpringBootTest(properties = "promotion.outbox-relay.enabled=false")
class ServerAApplicationTests {

	@Autowired
	private RabbitProperties rabbitProperties;

	@Autowired
	private HikariDataSource dataSource;

	@Test
	void contextLoads() {
	}

	@Test
	void rabbitPublisherConfirmIsEnabled() {
		assertThat(rabbitProperties.getPublisherConfirmType()).isEqualTo(ConfirmType.SIMPLE);
	}

	@Test
	void datasourcePoolSettingsAreBound() {
		assertThat(dataSource.getMaximumPoolSize()).isEqualTo(20);
		assertThat(dataSource.getMinimumIdle()).isEqualTo(5);
		assertThat(dataSource.getConnectionTimeout()).isEqualTo(3000);
	}
}
