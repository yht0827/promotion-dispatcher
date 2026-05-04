package com.promotion.servera.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {
}
