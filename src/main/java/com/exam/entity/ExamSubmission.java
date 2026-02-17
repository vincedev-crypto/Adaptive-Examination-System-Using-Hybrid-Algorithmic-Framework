package com.exam.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam_submissions")
public class ExamSubmission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "student_email", nullable = false)
    private String studentEmail;
    
    @Column(name = "exam_name", nullable = false)
    private String examName;
    
    @Column(name = "subject", nullable = false)
    private String subject; // Subject name (e.g., Mathematics, Science)
    
    @Column(name = "activity_type", nullable = false)
    private String activityType; // Assignment or Exam
    
    @Column(nullable = false)
    private int score;
    
    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;
    
    @Column(nullable = false)
    private double percentage;
    
    @Column(name = "results_released")
    private Boolean resultsReleased = false; // Teacher controls this
    
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    
    @Column(name = "released_at")
    private LocalDateTime releasedAt;
    
    @Column(name = "answer_details_json", columnDefinition = "TEXT")
    private String answerDetailsJson; // Store answer details as JSON
    
    // Analytics fields
    @Column(name = "topic_mastery")
    private double topicMastery;
    
    @Column(name = "difficulty_resilience")
    private double difficultyResilience;
    
    @Column(name = "accuracy")
    private double accuracy;
    
    @Column(name = "time_efficiency")
    private double timeEfficiency;
    
    @Column(name = "confidence")
    private double confidence;
    
    @Column(name = "performance_category")
    private String performanceCategory;
    
    // Manual grading fields for open-ended questions
    @Column(name = "manual_score")
    private Integer manualScore; // Points from teacher's manual grading
    
    @Column(name = "is_graded")
    private Boolean isGraded; // True when teacher finalizes the score
    
    @Column(name = "graded_at")
    private LocalDateTime gradedAt; // When teacher finalized the grading
    
    @Column(name = "teacher_comments", columnDefinition = "TEXT")
    private String teacherComments; // Optional feedback from teacher
    
    // Constructors
    public ExamSubmission() {
        this.submittedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
    
    public String getExamName() { return examName; }
    public void setExamName(String examName) { this.examName = examName; }
    
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    
    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }
    
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    
    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
    
    public double getPercentage() { return percentage; }
    public void setPercentage(double percentage) { 
        this.percentage = validateDouble(percentage);
    }
    
    public boolean isResultsReleased() { return resultsReleased != null && resultsReleased; }
    public void setResultsReleased(Boolean resultsReleased) { this.resultsReleased = resultsReleased; }
    
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime releasedAt) { this.releasedAt = releasedAt; }
    
    public String getAnswerDetailsJson() { return answerDetailsJson; }
    public void setAnswerDetailsJson(String answerDetailsJson) { this.answerDetailsJson = answerDetailsJson; }
    
    public double getTopicMastery() { return topicMastery; }
    public void setTopicMastery(double topicMastery) { 
        this.topicMastery = validateDouble(topicMastery);
    }
    
    public double getDifficultyResilience() { return difficultyResilience; }
    public void setDifficultyResilience(double difficultyResilience) { 
        this.difficultyResilience = validateDouble(difficultyResilience);
    }
    
    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { 
        this.accuracy = validateDouble(accuracy);
    }
    
    public double getTimeEfficiency() { return timeEfficiency; }
    public void setTimeEfficiency(double timeEfficiency) { 
        this.timeEfficiency = validateDouble(timeEfficiency);
    }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { 
        this.confidence = validateDouble(confidence);
    }
    
    public String getPerformanceCategory() { return performanceCategory; }
    public void setPerformanceCategory(String performanceCategory) { this.performanceCategory = performanceCategory; }
    
    public Integer getManualScore() { return manualScore; }
    public void setManualScore(Integer manualScore) { this.manualScore = manualScore; }
    
    public Boolean isGraded() { return isGraded != null && isGraded; }
    public void setGraded(Boolean isGraded) { this.isGraded = isGraded; }
    
    public LocalDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(LocalDateTime gradedAt) { this.gradedAt = gradedAt; }
    
    public String getTeacherComments() { return teacherComments; }
    public void setTeacherComments(String teacherComments) { this.teacherComments = teacherComments; }
    
    /**
     * Get final score (auto + manual if graded, otherwise just auto score)
     */
    public int getFinalScore() {
        if (isGraded && manualScore != null) {
            return score + manualScore;
        }
        return score;
    }
    
    /**
     * Get final percentage based on final score
     */
    public double getFinalPercentage() {
        if (totalQuestions > 0) {
            return (getFinalScore() * 100.0) / totalQuestions;
        }
        return 0.0;
    }
    
    /**
     * Validate double values to prevent NaN or Infinity from being saved to database
     */
    private double validateDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0; // Return default value instead of NaN/Infinity
        }
        return value;
    }
}
