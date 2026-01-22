package com.exam.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage answer keys for exams
 * Stores the correct answers for each distributed exam
 */
@Service
public class AnswerKeyService {
    
    // Map: studentId -> Map<questionNumber, correctAnswer>
    private Map<String, Map<Integer, String>> studentAnswerKeys = new ConcurrentHashMap<>();
    
    // Map: examId -> Map<questionNumber, correctAnswer>
    private Map<String, Map<Integer, String>> examAnswerKeys = new ConcurrentHashMap<>();
    
    /**
     * Store answer key for a specific student's exam
     * @param studentId The student ID
     * @param answerKey Map of question number to correct answer
     */
    public void storeStudentAnswerKey(String studentId, Map<Integer, String> answerKey) {
        studentAnswerKeys.put(studentId, new HashMap<>(answerKey));
    }
    
    /**
     * Get answer key for a specific student
     * @param studentId The student ID
     * @return Map of question number to correct answer, or null if not found
     */
    public Map<Integer, String> getStudentAnswerKey(String studentId) {
        return studentAnswerKeys.get(studentId);
    }
    
    /**
     * Store answer key for an exam ID (for general exam tracking)
     * @param examId The exam identifier
     * @param answerKey Map of question number to correct answer
     */
    public void storeExamAnswerKey(String examId, Map<Integer, String> answerKey) {
        examAnswerKeys.put(examId, new HashMap<>(answerKey));
    }
    
    /**
     * Get answer key for a specific exam
     * @param examId The exam identifier
     * @return Map of question number to correct answer, or null if not found
     */
    public Map<Integer, String> getExamAnswerKey(String examId) {
        return examAnswerKeys.get(examId);
    }
    
    /**
     * Grade a student's answers
     * @param studentId The student ID
     * @param studentAnswers Map of question number to student's answer
     * @return Score as percentage (0-100)
     */
    public double gradeExam(String studentId, Map<Integer, String> studentAnswers) {
        Map<Integer, String> correctAnswers = studentAnswerKeys.get(studentId);
        
        if (correctAnswers == null || correctAnswers.isEmpty()) {
            return 0.0;
        }
        
        int totalQuestions = correctAnswers.size();
        int correctCount = 0;
        
        for (Map.Entry<Integer, String> entry : correctAnswers.entrySet()) {
            Integer questionNum = entry.getKey();
            String correctAnswer = entry.getValue().trim().toLowerCase();
            
            if (studentAnswers.containsKey(questionNum)) {
                String studentAnswer = studentAnswers.get(questionNum).trim().toLowerCase();
                if (correctAnswer.equals(studentAnswer)) {
                    correctCount++;
                }
            }
        }
        
        return (correctCount * 100.0) / totalQuestions;
    }
    
    /**
     * Check if answer key exists for a student
     * @param studentId The student ID
     * @return true if answer key exists
     */
    public boolean hasAnswerKeyForStudent(String studentId) {
        return studentAnswerKeys.containsKey(studentId);
    }
    
    /**
     * Clear all stored answer keys (useful for testing)
     */
    public void clearAll() {
        studentAnswerKeys.clear();
        examAnswerKeys.clear();
    }
    
    /**
     * Remove answer key for a specific student
     * @param studentId The student ID
     */
    public void removeStudentAnswerKey(String studentId) {
        studentAnswerKeys.remove(studentId);
    }
}
