package com.project.sangngo552004.mailservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailJob {

    @Id
    private String id;

    @Column(name = "upload_job_id", nullable = false)
    private String uploadJobId;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Integer retryCount;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (status == null) {
            status = Status.PENDING;
        }
    }

    public enum Status {
        PENDING,
        SUCCESS,
        FAILED
    }
}
