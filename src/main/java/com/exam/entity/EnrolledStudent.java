package com.exam.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrolled_students")
public class EnrolledStudent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String teacherEmail;
    
    @Column(nullable = false)
    private String studentEmail;
    
    @Column(nullable = false)
    private String studentName;
    
    @Column(nullable = false)
    private LocalDateTime enrolledAt;
    
    // Constructors
    public EnrolledStudent() {
        this.enrolledAt = LocalDateTime.now();
    }
    
    public EnrolledStudent(String teacherEmail, String studentEmail, String studentName) {
        this.teacherEmail = teacherEmail;
        this.studentEmail = studentEmail;
        this.studentName = studentName;
        this.enrolledAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTeacherEmail() { return teacherEmail; }
    public void setTeacherEmail(String teacherEmail) { this.teacherEmail = teacherEmail; }
    
    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
    
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    
    public LocalDateTime getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(LocalDateTime enrolledAt) { this.enrolledAt = enrolledAt; }
}
