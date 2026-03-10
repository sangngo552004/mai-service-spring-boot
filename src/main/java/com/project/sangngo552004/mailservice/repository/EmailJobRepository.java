package com.project.sangngo552004.mailservice.repository;

import com.project.sangngo552004.mailservice.entity.EmailJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailJobRepository extends JpaRepository<EmailJob, String> {
}
