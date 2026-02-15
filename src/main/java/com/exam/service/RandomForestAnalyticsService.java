package com.exam.service;

import com.exam.entity.ExamSubmission;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TRUE Random Forest Algorithm Implementation for Student Performance Analysis
 * Based on research paper methodology using:
 * - Topic Mastery (TM): Σ Correct Answers in Topic / Total Attempts in Topic
 * - Difficulty Resilience (DR): Total Correct 'Hard' Questions / Total 'Hard' Questions Attempted
 * - Gini Impurity (G): 1 - Σ(pi²) for decision tree splits
 * - Ensemble learning with multiple decision trees
 */
@Service
public class RandomForestAnalyticsService {
    
    // Random Forest hyperparameters
    private static final int NUM_TREES = 100;  // Number of decision trees in forest
    private static final int MAX_DEPTH = 10;   // Maximum depth for each tree
    private static final double GINI_THRESHOLD = 0.1;  // Minimum Gini for splitting
    
    /**
     * Student performance features extracted from exam data
     */
    public static class StudentFeatures {
        public double topicMasteryPrimary;      // TM for primary topic area
        public double topicMasterySecondary;    // TM for secondary topic area
        public double topicMasteryGeneral;      // TM overall across all topics
        public double difficultyResilience;     // DR for hard questions
        public double accuracy;                 // Overall accuracy
        public double timeEfficiency;           // Time management
        public double confidence;               // Engagement level
        
        // Actual outcome
        public String actualCategory;           // "Pass", "Fail", "Risk", etc.
        
        public StudentFeatures() {}
    }
    
    /**
     * Decision Tree Node
     */
    private static class DecisionNode {
        String featureName;             // Which feature to split on
        double threshold;               // Split threshold value
        @SuppressWarnings("unused")     // Stored for debugging/analysis purposes
        double giniImpurity;            // Gini score at this node
        DecisionNode leftChild;         // Students <= threshold
        DecisionNode rightChild;        // Students > threshold
        String prediction;              // Leaf node prediction
        boolean isLeaf;                 // Is this a leaf node?
        
        public DecisionNode() {
            this.isLeaf = false;
        }
    }
    
    /**
     * Random Forest Model (ensemble of decision trees)
     */
    private List<DecisionNode> forest = new ArrayList<>();
    private boolean isTrained = false;
    
    /**
     * Formula 1: Calculate Topic Mastery (TM)
     * TM = Σ Correct Answers in Topic / Total Attempts in Topic
     */
    public double calculateTopicMastery(int correctInTopic, int totalAttemptsInTopic) {
        if (totalAttemptsInTopic == 0) return 0.0;
        return (double) correctInTopic / totalAttemptsInTopic;
    }
    
    /**
     * Formula 2: Calculate Difficulty Resilience (DR)
     * DR = Total Correct 'Hard' Questions / Total 'Hard' Questions Attempted
     */
    public double calculateDifficultyResilience(int hardCorrect, int hardTotal) {
        if (hardTotal == 0) return 0.0;
        return (double) hardCorrect / hardTotal;
    }
    
    /**
     * Formula 3: Calculate Gini Impurity (G)
     * G = 1 - Σ(pi²)
     * Where pi is the probability of class i at this node
     */
    public double calculateGiniImpurity(List<StudentFeatures> samples) {
        if (samples.isEmpty()) return 0.0;
        
        // Count occurrences of each category
        Map<String, Long> categoryCounts = samples.stream()
                .collect(Collectors.groupingBy(s -> s.actualCategory, Collectors.counting()));
        
        // Calculate Gini: G = 1 - Σ(pi²)
        double gini = 1.0;
        int totalSamples = samples.size();
        
        for (Long count : categoryCounts.values()) {
            double probability = (double) count / totalSamples;
            gini -= (probability * probability);
        }
        
        return gini;
    }
    
    /**
     * Extract features from exam submission data
     * This processes the exam data similar to S10 dataset methodology
     */
    public StudentFeatures extractFeatures(ExamSubmission submission, 
                                          List<String> questionTopics,
                                          List<String> questionDifficulties,
                                          String answerDetailsJson) {
        StudentFeatures features = new StudentFeatures();
        
        // Parse answer details: "questionNum|studentAnswer|correctAnswer|isCorrect"
        String[] answerDetails = answerDetailsJson != null ? answerDetailsJson.split(";") : new String[0];
        
        // Count by topic and difficulty
        Map<String, Integer> topicTotals = new HashMap<>();
        Map<String, Integer> topicCorrects = new HashMap<>();
        int hardTotal = 0, hardCorrect = 0;
        int totalAttempts = 0, totalCorrect = 0;
        
        for (int i = 0; i < answerDetails.length; i++) {
            if (answerDetails[i].trim().isEmpty()) continue;
            
            String[] parts = answerDetails[i].split("\\|");
            if (parts.length < 4) continue;
            
            try {
                int questionNum = Integer.parseInt(parts[0]) - 1; // Convert to 0-based index
                boolean isCorrect = Boolean.parseBoolean(parts[3]);
                
                totalAttempts++;
                if (isCorrect) totalCorrect++;
                
                // Get topic and difficulty for this question
                String topic = (questionNum < questionTopics.size()) ? 
                              questionTopics.get(questionNum) : "General";
                String difficulty = (questionNum < questionDifficulties.size()) ? 
                                   questionDifficulties.get(questionNum) : "Medium";
                
                // Count by topic
                topicTotals.put(topic, topicTotals.getOrDefault(topic, 0) + 1);
                if (isCorrect) {
                    topicCorrects.put(topic, topicCorrects.getOrDefault(topic, 0) + 1);
                }
                
                // Count hard questions
                if (difficulty.equalsIgnoreCase("Hard")) {
                    hardTotal++;
                    if (isCorrect) hardCorrect++;
                }
            } catch (Exception e) {
                // Skip malformed answer details
                continue;
            }
        }
        
        // Find primary and secondary topics (topics with most questions)
        List<Map.Entry<String, Integer>> sortedTopics = topicTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
        
        // Calculate Topic Mastery for different areas
        if (!sortedTopics.isEmpty()) {
            String primaryTopic = sortedTopics.get(0).getKey();
            int primaryTotal = topicTotals.get(primaryTopic);
            int primaryCorrect = topicCorrects.getOrDefault(primaryTopic, 0);
            features.topicMasteryPrimary = calculateTopicMastery(primaryCorrect, primaryTotal);
        } else {
            features.topicMasteryPrimary = 0.0;
        }
        
        if (sortedTopics.size() > 1) {
            String secondaryTopic = sortedTopics.get(1).getKey();
            int secondaryTotal = topicTotals.get(secondaryTopic);
            int secondaryCorrect = topicCorrects.getOrDefault(secondaryTopic, 0);
            features.topicMasterySecondary = calculateTopicMastery(secondaryCorrect, secondaryTotal);
        } else {
            features.topicMasterySecondary = features.topicMasteryPrimary;
        }
        
        // Calculate general Topic Mastery
        features.topicMasteryGeneral = calculateTopicMastery(totalCorrect, totalAttempts);
        
        // Calculate Difficulty Resilience
        features.difficultyResilience = calculateDifficultyResilience(hardCorrect, hardTotal);
        
        // Additional features from submission (convert Double to double, handling null)
        Double accuracyObj = submission.getAccuracy();
        features.accuracy = (accuracyObj != null) ? accuracyObj : 0.0;
        
        Double timeEfficiencyObj = submission.getTimeEfficiency();
        features.timeEfficiency = (timeEfficiencyObj != null) ? timeEfficiencyObj : 0.0;
        
        Double confidenceObj = submission.getConfidence();
        features.confidence = (confidenceObj != null) ? confidenceObj : 0.0;
        
        // Determine actual category based on performance
        features.actualCategory = determineCategory(features);
        
        return features;
    }
    
    /**
     * Determine performance category based on features
     */
    private String determineCategory(StudentFeatures features) {
        double overallScore = features.topicMasteryGeneral * 100;
        
        // Check for subject-specific risks (as described in research paper)
        if (features.topicMasteryPrimary < 0.5 && features.topicMasterySecondary > 0.8) {
            return "Topic Risk - Primary Area";
        } else if (features.topicMasterySecondary < 0.5 && features.topicMasteryPrimary > 0.8) {
            return "Topic Risk - Secondary Area";
        } else if (features.difficultyResilience < 0.4) {
            return "Difficulty Risk";
        } else if (overallScore >= 90) {
            return "Excellent";
        } else if (overallScore >= 70) {
            return "Pass";
        } else if (overallScore >= 50) {
            return "Partial Mastery";
        } else {
            return "Fail";
        }
    }
    
    /**
     * Get feature value from StudentFeatures object
     */
    private double getFeatureValue(StudentFeatures sample, String featureName) {
        switch (featureName) {
            case "topicMasteryPrimary": return sample.topicMasteryPrimary;
            case "topicMasterySecondary": return sample.topicMasterySecondary;
            case "topicMasteryGeneral": return sample.topicMasteryGeneral;
            case "difficultyResilience": return sample.difficultyResilience;
            case "accuracy": return sample.accuracy / 100.0;
            case "timeEfficiency": return sample.timeEfficiency / 100.0;
            case "confidence": return sample.confidence / 100.0;
            default: return 0.0;
        }
    }
    
    /**
     * Find the best feature and threshold to split on
     * Uses Gini Impurity to determine optimal split
     */
    private Map<String, Object> findBestSplit(List<StudentFeatures> samples) {
        double bestGini = Double.MAX_VALUE;
        String bestFeature = null;
        double bestThreshold = 0.0;
        List<StudentFeatures> bestLeft = null;
        List<StudentFeatures> bestRight = null;
        
        // Try splitting on each feature
        String[] features = {"topicMasteryPrimary", "topicMasterySecondary", 
                            "topicMasteryGeneral", "difficultyResilience", 
                            "accuracy", "timeEfficiency", "confidence"};
        
        for (String feature : features) {
            // Try different threshold values
            double[] thresholds = {0.3, 0.5, 0.7, 0.8};
            
            for (double threshold : thresholds) {
                // Split samples
                List<StudentFeatures> left = new ArrayList<>();
                List<StudentFeatures> right = new ArrayList<>();
                
                for (StudentFeatures sample : samples) {
                    double value = getFeatureValue(sample, feature);
                    if (value <= threshold) {
                        left.add(sample);
                    } else {
                        right.add(sample);
                    }
                }
                
                // Skip if split doesn't separate data
                if (left.isEmpty() || right.isEmpty()) continue;
                
                // Calculate weighted Gini impurity
                double giniLeft = calculateGiniImpurity(left);
                double giniRight = calculateGiniImpurity(right);
                double weightedGini = (left.size() * giniLeft + right.size() * giniRight) / samples.size();
                
                // Update best split if this is better
                if (weightedGini < bestGini) {
                    bestGini = weightedGini;
                    bestFeature = feature;
                    bestThreshold = threshold;
                    bestLeft = left;
                    bestRight = right;
                }
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("feature", bestFeature);
        result.put("threshold", bestThreshold);
        result.put("gini", bestGini);
        result.put("left", bestLeft);
        result.put("right", bestRight);
        
        return result;
    }
    
    /**
     * Get majority class from samples
     */
    private String getMajorityClass(List<StudentFeatures> samples) {
        if (samples.isEmpty()) return "Unknown";
        
        return samples.stream()
                .collect(Collectors.groupingBy(s -> s.actualCategory, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");
    }
    
    /**
     * Build a decision tree recursively
     */
    private DecisionNode buildTree(List<StudentFeatures> samples, int depth) {
        DecisionNode node = new DecisionNode();
        
        // Base cases for stopping recursion
        if (samples.isEmpty() || depth >= MAX_DEPTH) {
            node.isLeaf = true;
            node.prediction = getMajorityClass(samples);
            return node;
        }
        
        // Check if all samples have same category (pure node)
        Set<String> uniqueCategories = samples.stream()
                .map(s -> s.actualCategory)
                .collect(Collectors.toSet());
        
        if (uniqueCategories.size() == 1) {
            node.isLeaf = true;
            node.prediction = samples.get(0).actualCategory;
            return node;
        }
        
        // Find best split
        Map<String, Object> split = findBestSplit(samples);
        
        if (split.get("feature") == null) {
            node.isLeaf = true;
            node.prediction = getMajorityClass(samples);
            return node;
        }
        
        double gini = (Double) split.get("gini");
        
        // Stop if Gini is too low (pure enough)
        if (gini < GINI_THRESHOLD) {
            node.isLeaf = true;
            node.prediction = getMajorityClass(samples);
            return node;
        }
        
        // Create internal node
        node.featureName = (String) split.get("feature");
        node.threshold = (Double) split.get("threshold");
        node.giniImpurity = gini;
        
        @SuppressWarnings("unchecked")
        List<StudentFeatures> leftSamples = (List<StudentFeatures>) split.get("left");
        @SuppressWarnings("unchecked")
        List<StudentFeatures> rightSamples = (List<StudentFeatures>) split.get("right");
        
        // Recursively build child nodes
        node.leftChild = buildTree(leftSamples, depth + 1);
        node.rightChild = buildTree(rightSamples, depth + 1);
        
        return node;
    }
    
    /**
     * Predict category for a single student using one tree
     */
    private String predictWithTree(DecisionNode tree, StudentFeatures features) {
        if (tree == null || tree.isLeaf) {
            return tree != null ? tree.prediction : "Unknown";
        }
        
        double featureValue = getFeatureValue(features, tree.featureName);
        
        if (featureValue <= tree.threshold) {
            return predictWithTree(tree.leftChild, features);
        } else {
            return predictWithTree(tree.rightChild, features);
        }
    }
    
    /**
     * Train Random Forest on historical student data
     * This builds NUM_TREES decision trees using bootstrap sampling
     */
    public void trainRandomForest(List<StudentFeatures> historicalData) {
        System.out.println("\n=== TRAINING RANDOM FOREST ===");
        System.out.println("Training on " + historicalData.size() + " historical student records");
        System.out.println("Number of trees: " + NUM_TREES);
        System.out.println("Max depth: " + MAX_DEPTH);
        
        forest.clear();
        Random random = new Random(42);  // Fixed seed for reproducibility
        
        for (int i = 0; i < NUM_TREES; i++) {
            // Bootstrap sampling: randomly sample with replacement
            List<StudentFeatures> bootstrapSample = new ArrayList<>();
            for (int j = 0; j < historicalData.size(); j++) {
                int randomIndex = random.nextInt(historicalData.size());
                bootstrapSample.add(historicalData.get(randomIndex));
            }
            
            // Build decision tree
            DecisionNode tree = buildTree(bootstrapSample, 0);
            forest.add(tree);
            
            if ((i + 1) % 20 == 0) {
                System.out.println("Built " + (i + 1) + "/" + NUM_TREES + " trees...");
            }
        }
        
        isTrained = true;
        System.out.println("✅ Random Forest training complete!\n");
    }
    
    /**
     * Predict category for new student using the entire forest (ensemble voting)
     */
    public String predictCategory(StudentFeatures features) {
        // If not trained, use rule-based classification
        if (!isTrained || forest.isEmpty()) {
            return determineCategory(features);
        }
        
        // Get predictions from all trees
        Map<String, Integer> votes = new HashMap<>();
        
        for (DecisionNode tree : forest) {
            String prediction = predictWithTree(tree, features);
            votes.put(prediction, votes.getOrDefault(prediction, 0) + 1);
        }
        
        // Return majority vote
        return votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(determineCategory(features));
    }
    
    /**
     * Generate detailed analysis report for a student
     * This is what displays in the performance analytics screen
     */
    public Map<String, Object> generateStudentReport(StudentFeatures features) {
        Map<String, Object> report = new HashMap<>();
        
        // Predict category using Random Forest (or rule-based if not trained)
        String predictedCategory = predictCategory(features);
        
        // Calculate individual metrics (as percentages)
        report.put("topicMasteryPrimary", features.topicMasteryPrimary * 100);
        report.put("topicMasterySecondary", features.topicMasterySecondary * 100);
        report.put("topicMasteryGeneral", features.topicMasteryGeneral * 100);
        report.put("difficultyResilience", features.difficultyResilience * 100);
        report.put("accuracy", features.accuracy);
        report.put("timeEfficiency", features.timeEfficiency);
        report.put("confidence", features.confidence);
        
        // Overall score (weighted average matching research paper)
        double overallScore = 
            (features.topicMasteryGeneral * 0.30) +
            (features.difficultyResilience * 0.25) +
            (features.accuracy / 100.0 * 0.20) +
            (features.timeEfficiency / 100.0 * 0.15) +
            (features.confidence / 100.0 * 0.10);
        
        report.put("overallScore", overallScore * 100);
        report.put("predictedCategory", predictedCategory);
        
        // Identify strengths and weaknesses
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        
        if (features.topicMasteryPrimary >= 0.6) strengths.add("Primary Topic Mastery");
        else if (features.topicMasteryPrimary < 0.4) weaknesses.add("Primary Topic Understanding");
        
        if (features.topicMasterySecondary >= 0.6) strengths.add("Secondary Topic Mastery");
        else if (features.topicMasterySecondary < 0.4) weaknesses.add("Secondary Topic Understanding");
        
        if (features.difficultyResilience >= 0.6) strengths.add("Handles Difficult Questions");
        else if (features.difficultyResilience < 0.4) weaknesses.add("Struggles with Hard Questions");
        
        if (features.timeEfficiency >= 60) strengths.add("Time Management");
        if (features.confidence >= 80) strengths.add("High Engagement");
        
        report.put("strengths", strengths);
        report.put("weaknesses", weaknesses);
        
        // Generate recommendations
        List<String> recommendations = generateRecommendations(features, predictedCategory);
        report.put("recommendations", recommendations);
        
        return report;
    }
    
    /**
     * Generate personalized recommendations based on Random Forest analysis
     */
    private List<String> generateRecommendations(StudentFeatures features, String category) {
        List<String> recommendations = new ArrayList<>();
        
        if (category.contains("Topic Risk - Primary")) {
            recommendations.add("Focus on primary topic area - review fundamental concepts");
            recommendations.add("Practice more questions in this subject before next assessment");
        } else if (category.contains("Topic Risk - Secondary")) {
            recommendations.add("Strengthen secondary topic knowledge");
            recommendations.add("Review related concepts and examples");
        } else if (category.equals("Difficulty Risk")) {
            recommendations.add("Practice harder questions to build resilience");
            recommendations.add("Work on problem-solving strategies");
            recommendations.add("Seek help with challenging concepts");
        }
        
        if (features.difficultyResilience < 0.5 && !category.equals("Difficulty Risk")) {
            recommendations.add("Continue practicing hard questions");
        }
        
        if (features.timeEfficiency < 50) {
            recommendations.add("Work on time management during exams");
        }
        
        if (features.confidence < 70) {
            recommendations.add("Attempt more questions to build confidence");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Excellent performance! Keep up the good work.");
        }
        
        return recommendations;
    }
    
    /**
     * Check if Random Forest is trained
     */
    public boolean isTrained() {
        return isTrained;
    }
}
