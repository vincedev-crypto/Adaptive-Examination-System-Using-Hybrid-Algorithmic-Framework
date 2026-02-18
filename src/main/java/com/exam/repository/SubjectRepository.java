package com.exam.repository;

import com.exam.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    
    List<Subject> findByTeacherEmail(String teacherEmail);
    
    Optional<Subject> findBySubjectNameAndTeacherEmail(String subjectName, String teacherEmail);
    
    boolean existsBySubjectNameAndTeacherEmail(String subjectName, String teacherEmail);
}
