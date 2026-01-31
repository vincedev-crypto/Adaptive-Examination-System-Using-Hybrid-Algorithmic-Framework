package com.exam.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam_submissions")
public class ExamSubmission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String studentEmail;
    
    @Column(nullable = false)
    private String examName;
    
    @Column(nullable = false)
    private int score;
    
    @Column(nullable = false)
    private int totalQuestions;
    
    @Column(nullable = false)
    private double percentage;
    
    @Column(nullable = false)
    private boolean resultsReleased = false; // Teacher controls this
    
    @Column(nullable = false)
    private LocalDateTime submittedAt;
    
    private LocalDateTime releasedAt;
    
    @Column(columnDefinition = "TEXT")
    private String answerDetailsJson; // Store answer details as JSON
    
    // Analytics fields
    private double topicMastery;
    private double difficultyResilience;
    private double accuracy;
    private double timeEfficiency;
    private double confidence;
    private String performanceCategory;
    
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
    
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    
    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
    
    public double getPercentage() { return percentage; }
    public void setPercentage(double percentage) { 
        this.percentage = validateDouble(percentage);
    }
    
    public boolean isResultsReleased() { return resultsReleased; }
    public void setResultsReleased(boolean resultsReleased) { this.resultsReleased = resultsReleased; }
    
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
