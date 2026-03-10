package com.project.sangngo552004.mailservice.repository;

import com.project.sangngo552004.mailservice.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByStatusOrderByCreatedAt(OutboxEvent.Status status, Pageable pageable);
}
