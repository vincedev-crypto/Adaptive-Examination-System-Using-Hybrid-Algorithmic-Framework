package com.exam.service;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.*;

/**
 * Fisher-Yates Shuffle Algorithm Service
 * 
 * Implements the Fisher-Yates (Knuth) shuffle algorithm for randomizing
 * question choices to prevent cheating while maintaining answer key integrity.
 */
@Service
public class FisherYatesService {

    private final SecureRandom random = new SecureRandom();
    
    /**
     * Represents a question with its choices
     */
    public static class Question {
        private int id;
        private String text;
        private List<String> choices;
        private String correctAnswer;
        
        public Question(int id, String text, List<String> choices, String correctAnswer) {
            this.id = id;
            this.text = text;
            this.choices = new ArrayList<>(choices);
            this.correctAnswer = correctAnswer;
        }
        
        public int getId() { return id; }
        public String getText() { return text; }
        public List<String> getChoices() { return choices; }
        public String getCorrectAnswer() { return correctAnswer; }
        
        public void setChoices(List<String> choices) { this.choices = choices; }
        
        public String formatQuestion() {
            StringBuilder sb = new StringBuilder();
            sb.append(text);
            char label = 'A';
            for (String choice : choices) {
                sb.append("\n").append(label).append(") ").append(choice);
                label++;
            }
            return sb.toString();
        }
    }
    
    /**
     * Shuffle a list using Fisher-Yates algorithm
     * Time complexity: O(n)
     * Space complexity: O(1)
     */
    public <T> void shuffle(List<T> list) {
        shuffle(list, random);
    }
    
    /**
     * Shuffle a list using Fisher-Yates algorithm with custom random generator
     */
    public <T> void shuffle(List<T> list, Random rng) {
        int n = list.size();
        
        // Fisher-Yates shuffle: iterate from last to first
        for (int i = n - 1; i > 0; i--) {
            // Pick a random index from 0 to i (inclusive)
            int j = rng.nextInt(i + 1);
            
            // Swap elements at positions i and j
            T temp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, temp);
        }
    }
    
    /**
     * Shuffle question choices while preserving the correct answer
     */
    public Question shuffleQuestion(Question question) {
        List<String> shuffledChoices = new ArrayList<>(question.getChoices());
        shuffle(shuffledChoices);
        
        Question shuffled = new Question(
            question.getId(),
            question.getText(),
            shuffledChoices,
            question.getCorrectAnswer()
        );
        
        return shuffled;
    }
    
    /**
     * Shuffle multiple questions' choices
     */
    public List<Question> shuffleQuestions(List<Question> questions) {
        List<Question> shuffledQuestions = new ArrayList<>();
        
        for (Question question : questions) {
            shuffledQuestions.add(shuffleQuestion(question));
        }
        
        return shuffledQuestions;
    }
    
    /**
     * Shuffle the order of questions (use with caution - ensure answer key is updated)
     */
    public List<Question> shuffleQuestionOrder(List<Question> questions) {
        List<Question> shuffled = new ArrayList<>(questions);
        shuffle(shuffled);
        return shuffled;
    }
    
    /**
     * Create a randomized exam with shuffled choices
     * Returns formatted strings for each question
     */
    public List<String> createRandomizedExam(List<Question> questions) {
        List<String> exam = new ArrayList<>();
        
        for (Question question : questions) {
            Question shuffled = shuffleQuestion(question);
            exam.add(shuffled.formatQuestion());
        }
        
        return exam;
    }
    
    /**
     * Generate an answer key map from questions
     */
    public Map<Integer, String> generateAnswerKey(List<Question> questions) {
        Map<Integer, String> answerKey = new HashMap<>();
        
        for (Question question : questions) {
            answerKey.put(question.getId(), question.getCorrectAnswer());
        }
        
        return answerKey;
    }
    
    /**
     * Verify Fisher-Yates implementation by checking distribution
     * (For testing purposes)
     */
    public Map<String, Integer> testShuffleDistribution(List<String> items, int iterations) {
        Map<String, Integer> positionCounts = new HashMap<>();
        
        for (int iter = 0; iter < iterations; iter++) {
            List<String> copy = new ArrayList<>(items);
            shuffle(copy);
            
            // Count how many times each item appears in first position
            String firstItem = copy.get(0);
            positionCounts.put(firstItem, positionCounts.getOrDefault(firstItem, 0) + 1);
        }
        
        return positionCounts;
    }
    
    /**
     * Get statistics about shuffle fairness
     */
    public String getShuffleStatistics(List<String> items, int iterations) {
        Map<String, Integer> distribution = testShuffleDistribution(items, iterations);
        
        StringBuilder stats = new StringBuilder();
        stats.append("Fisher-Yates Shuffle Statistics (").append(iterations).append(" iterations):\n");
        
        double expectedFrequency = (double) iterations / items.size();
        
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            double actualFrequency = entry.getValue();
            double deviation = Math.abs(actualFrequency - expectedFrequency) / expectedFrequency * 100;
            
            stats.append(String.format("%s: %d times (%.2f%% deviation from expected)\n",
                entry.getKey(), entry.getValue(), deviation));
        }
        
        return stats.toString();
    }
}
