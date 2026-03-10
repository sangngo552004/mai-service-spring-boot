package com.project.sangngo552004.mailservice.repository;

import com.project.sangngo552004.mailservice.entity.UploadJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UploadJobRepository extends JpaRepository<UploadJob, String> {
    List<UploadJob> findByStatusOrderByCreatedAt(UploadJob.Status status, Pageable pageable);
}
