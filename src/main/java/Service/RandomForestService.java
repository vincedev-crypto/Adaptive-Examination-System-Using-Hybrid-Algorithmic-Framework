package Service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class RandomForestService {

    /**
     * Calculate student analytics using Random Forest-inspired metrics
     * Based on exam performance data
     */
    public StudentAnalytics calculateStudentAnalytics(String studentId, List<String> answers, 
                                                      Map<Integer, String> answerKey, 
                                                      List<Long> timeTaken) {
        
        // Feature Extraction
        int totalQuestions = answerKey.size();
        int correctAnswers = calculateCorrectAnswers(answers, answerKey);
        double accuracy = (totalQuestions > 0) ? (correctAnswers * 100.0 / totalQuestions) : 0;
        
        // Topic Mastery: Group questions by difficulty/topic
        Map<String, Double> topicScores = calculateTopicMastery(answers, answerKey);
        double avgTopicMastery = topicScores.values().stream()
            .mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        // Difficulty Resilience: Performance on hard vs easy questions
        double difficultyResilience = calculateDifficultyResilience(answers, answerKey);
        
        // Time Efficiency: Average time per question (normalized)
        double timeEfficiency = calculateTimeEfficiency(timeTaken, totalQuestions);
        
        // Confidence Score: Based on answer patterns and consistency
        double confidence = calculateConfidence(answers, correctAnswers, totalQuestions);
        
        // Random Forest Classification (simulated decision tree ensemble)
        String performanceCategory = classifyPerformance(
            avgTopicMastery, difficultyResilience, accuracy, timeEfficiency, confidence
        );
        
        return new StudentAnalytics(
            studentId,
            avgTopicMastery,
            difficultyResilience,
            accuracy,
            timeEfficiency,
            confidence,
            performanceCategory
        );
    }
    
    private int calculateCorrectAnswers(List<String> answers, Map<Integer, String> key) {
        int correct = 0;
        for (int i = 0; i < answers.size(); i++) {
            if (key.containsKey(i + 1) && answers.get(i).equals(key.get(i + 1))) {
                correct++;
            }
        }
        return correct;
    }
    
    /**
     * Topic Mastery: Analyze performance across different topics/difficulty levels
     */
    private Map<String, Double> calculateTopicMastery(List<String> answers, Map<Integer, String> key) {
        Map<String, Double> topicScores = new HashMap<>();
        
        // Simulate topic grouping (in real scenario, questions would have topic tags)
        int questionsPerTopic = Math.max(1, key.size() / 5);
        String[] topics = {"Mathematics", "Science", "History", "Logic", "General"};
        
        for (int i = 0; i < topics.length; i++) {
            int startIdx = i * questionsPerTopic;
            int endIdx = Math.min(startIdx + questionsPerTopic, answers.size());
            
            if (startIdx < answers.size()) {
                int correct = 0;
                int total = 0;
                
                for (int j = startIdx; j < endIdx && j < answers.size(); j++) {
                    if (key.containsKey(j + 1) && answers.get(j).equals(key.get(j + 1))) {
                        correct++;
                    }
                    total++;
                }
                
                double score = total > 0 ? (correct * 100.0 / total) : 0;
                topicScores.put(topics[i], score);
            }
        }
        
        return topicScores;
    }
    
    /**
     * Difficulty Resilience: Performance on harder questions vs easier ones
     */
    private double calculateDifficultyResilience(List<String> answers, Map<Integer, String> key) {
        // Simulate difficulty levels (first half = easy, second half = hard)
        int midpoint = answers.size() / 2;
        
        int easyCorrect = 0, hardCorrect = 0;
        int easyTotal = 0, hardTotal = 0;
        
        for (int i = 0; i < answers.size(); i++) {
            if (key.containsKey(i + 1) && answers.get(i).equals(key.get(i + 1))) {
                if (i < midpoint) easyCorrect++;
                else hardCorrect++;
            }
            
            if (i < midpoint) easyTotal++;
            else hardTotal++;
        }
        
        double easyScore = easyTotal > 0 ? (easyCorrect * 100.0 / easyTotal) : 0;
        double hardScore = hardTotal > 0 ? (hardCorrect * 100.0 / hardTotal) : 0;
        
        // Resilience: how well they handle difficult questions
        return hardScore > 0 ? (hardScore / Math.max(easyScore, 1)) * 100 : 0;
    }
    
    /**
     * Time Efficiency: Normalized time performance
     */
    private double calculateTimeEfficiency(List<Long> timeTaken, int totalQuestions) {
        if (timeTaken == null || timeTaken.isEmpty()) return 75.0; // Default
        
        double avgTime = timeTaken.stream().mapToLong(Long::longValue).average().orElse(60.0);
        double optimalTime = 45.0; // seconds per question
        
        // Normalize: 100% if at optimal time, decrease if too fast or too slow
        double efficiency = 100 - Math.abs(avgTime - optimalTime) * 2;
        return Math.max(0, Math.min(100, efficiency));
    }
    
    /**
     * Confidence: Based on answer consistency and patterns
     */
    private double calculateConfidence(List<String> answers, int correctAnswers, int totalQuestions) {
        // Higher confidence if more questions answered and higher accuracy
        double completionRate = answers.size() * 100.0 / totalQuestions;
        double accuracyRate = correctAnswers * 100.0 / Math.max(1, totalQuestions);
        
        return (completionRate * 0.3 + accuracyRate * 0.7);
    }
    
    /**
     * Random Forest Classification: Ensemble decision making
     * Simulates multiple decision trees voting on performance category
     */
    private String classifyPerformance(double topicMastery, double resilience, 
                                      double accuracy, double timeEff, double confidence) {
        int excellentVotes = 0;
        int goodVotes = 0;
        int fairVotes = 0;
        int poorVotes = 0;
        
        // Decision Tree 1: Accuracy-focused
        if (accuracy >= 85) excellentVotes++;
        else if (accuracy >= 70) goodVotes++;
        else if (accuracy >= 50) fairVotes++;
        else poorVotes++;
        
        // Decision Tree 2: Consistency-focused
        double consistency = (topicMastery + confidence) / 2;
        if (consistency >= 80) excellentVotes++;
        else if (consistency >= 65) goodVotes++;
        else if (consistency >= 45) fairVotes++;
        else poorVotes++;
        
        // Decision Tree 3: Resilience-focused
        if (resilience >= 75 && timeEff >= 70) excellentVotes++;
        else if (resilience >= 60) goodVotes++;
        else if (resilience >= 40) fairVotes++;
        else poorVotes++;
        
        // Decision Tree 4: Overall balance
        double overall = (topicMastery + resilience + accuracy + timeEff + confidence) / 5;
        if (overall >= 80) excellentVotes++;
        else if (overall >= 65) goodVotes++;
        else if (overall >= 45) fairVotes++;
        else poorVotes++;
        
        // Majority voting
        int maxVotes = Math.max(Math.max(excellentVotes, goodVotes), 
                               Math.max(fairVotes, poorVotes));
        
        if (maxVotes == excellentVotes) return "Excellent";
        if (maxVotes == goodVotes) return "Good";
        if (maxVotes == fairVotes) return "Fair";
        return "Needs Improvement";
    }
    
    /**
     * Get historical performance data for trend analysis
     */
    public List<HistoricalPerformance> getHistoricalData(String studentId) {
        // Simulated historical data (replace with database query)
        List<HistoricalPerformance> history = new ArrayList<>();
        
        Random rand = new Random();
        for (int i = 5; i >= 0; i--) {
            history.add(new HistoricalPerformance(
                "Exam " + (6 - i),
                70 + rand.nextInt(25)
            ));
        }
        
        return history;
    }
    
    // Inner classes for data transfer
    public static class StudentAnalytics {
        public String studentId;
        public double topicMastery;
        public double difficultyResilience;
        public double accuracy;
        public double timeEfficiency;
        public double confidence;
        public String performanceCategory;
        
        public StudentAnalytics(String studentId, double tm, double dr, double acc, 
                              double te, double conf, String category) {
            this.studentId = studentId;
            this.topicMastery = tm;
            this.difficultyResilience = dr;
            this.accuracy = acc;
            this.timeEfficiency = te;
            this.confidence = conf;
            this.performanceCategory = category;
        }
        
        // Getters for Thymeleaf
        public String getStudentId() { return studentId; }
        public double getTopicMastery() { return topicMastery; }
        public double getDifficultyResilience() { return difficultyResilience; }
        public double getAccuracy() { return accuracy; }
        public double getTimeEfficiency() { return timeEfficiency; }
        public double getConfidence() { return confidence; }
        public String getPerformanceCategory() { return performanceCategory; }
    }
    
    public static class HistoricalPerformance {
        public String examName;
        public double score;
        
        public HistoricalPerformance(String examName, double score) {
            this.examName = examName;
            this.score = score;
        }
        
        public String getExamName() { return examName; }
        public double getScore() { return score; }
    }
}
