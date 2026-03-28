package com.project.sangngo552004.mailservice.repository;

import com.project.sangngo552004.mailservice.entity.EmailJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailJobRepository extends JpaRepository<EmailJob, String> {
    long countByUploadJobId(String uploadJobId);

    long countByUploadJobIdAndStatus(String uploadJobId, EmailJob.Status status);

    List<EmailJob> findAllByUploadJobId(String uploadJobId);
}
