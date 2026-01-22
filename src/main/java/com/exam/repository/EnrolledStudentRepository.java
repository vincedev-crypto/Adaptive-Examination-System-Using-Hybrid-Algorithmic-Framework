package com.exam.repository;

import com.exam.entity.EnrolledStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrolledStudentRepository extends JpaRepository<EnrolledStudent, Long> {
    List<EnrolledStudent> findByTeacherEmail(String teacherEmail);
    Optional<EnrolledStudent> findByTeacherEmailAndStudentEmail(String teacherEmail, String studentEmail);
    void deleteById(Long id);
}
