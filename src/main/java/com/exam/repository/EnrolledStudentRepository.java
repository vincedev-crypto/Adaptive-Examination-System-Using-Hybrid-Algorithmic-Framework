package com.exam.repository;

import com.exam.entity.EnrolledStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrolledStudentRepository extends JpaRepository<EnrolledStudent, Long> {
    List<EnrolledStudent> findByTeacherEmail(String teacherEmail);
    List<EnrolledStudent> findBySubjectId(Long subjectId);
    Optional<EnrolledStudent> findByTeacherEmailAndStudentEmail(String teacherEmail, String studentEmail);
    Optional<EnrolledStudent> findByTeacherEmailAndStudentEmailAndSubjectId(String teacherEmail, String studentEmail, Long subjectId);
    void deleteById(Long id);
}
