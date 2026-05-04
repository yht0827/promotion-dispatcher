package com.promotion.servera.adapter.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

	List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

	Optional<OutboxEventEntity> findByEventId(String eventId);
}
