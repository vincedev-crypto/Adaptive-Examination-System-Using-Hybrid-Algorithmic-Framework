package com.exam.repository;

import com.exam.entity.ExamSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {
    List<ExamSubmission> findByStudentEmail(String studentEmail);
    Optional<ExamSubmission> findByStudentEmailAndExamName(String studentEmail, String examName);
    List<ExamSubmission> findByResultsReleasedFalse(); // Pending release
    List<ExamSubmission> findAll();
}
