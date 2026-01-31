package com.exam.Controller;

import Service.RandomForestService;
import com.exam.entity.ExamSubmission;
import com.exam.repository.ExamSubmissionRepository;
import com.exam.service.AnswerKeyService;
import com.exam.service.IRT3PLService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private RandomForestService randomForestService;
    
    @Autowired
    private IRT3PLService irt3PLService;
    
    @Autowired
    private AnswerKeyService answerKeyService;
    
    @Autowired
    private ExamSubmissionRepository examSubmissionRepository;

    // This would normally be injected from a service, but for now we'll reference the static map
    // In production, this should be stored in database
    private static Map<String, List<String>> getDistributedExams() {
        // This is a workaround - in production use a service/repository
        return com.exam.algo.HomepageController.getDistributedExams();
    }

    @GetMapping("/dashboard")
    public String studentDashboard(HttpSession session, Model model, java.security.Principal principal) {
        String studentId = principal.getName();
        model.addAttribute("studentEmail", studentId);
        model.addAttribute("hasExam", getDistributedExams().containsKey(studentId));
        
        // Check if student has submitted exams
        List<ExamSubmission> submissions = examSubmissionRepository.findByStudentEmail(studentId);
        model.addAttribute("hasSubmission", !submissions.isEmpty());
        model.addAttribute("submissions", submissions);
        
        return "student-dashboard";
    }
    
    @GetMapping("/results/{submissionId}")
    public String viewResults(@PathVariable Long submissionId, Model model, java.security.Principal principal) {
        String studentId = principal.getName();
        
        Optional<ExamSubmission> submissionOpt = examSubmissionRepository.findById(submissionId);
        
        if (submissionOpt.isEmpty() || !submissionOpt.get().getStudentEmail().equals(studentId)) {
            model.addAttribute("error", "Submission not found");
            return "redirect:/student/dashboard";
        }
        
        ExamSubmission submission = submissionOpt.get();
        
        // Parse answer details
        List<Map<String, Object>> answerDetails = new ArrayList<>();
        if (submission.getAnswerDetailsJson() != null && !submission.getAnswerDetailsJson().isEmpty()) {
            String[] details = submission.getAnswerDetailsJson().split(";");
            for (String detail : details) {
                if (!detail.trim().isEmpty()) {
                    String[] parts = detail.split("\\|");
                    if (parts.length == 4) {
                        Map<String, Object> detailMap = new HashMap<>();
                        detailMap.put("questionNumber", Integer.parseInt(parts[0]));
                        detailMap.put("studentAnswer", parts[1]);
                        detailMap.put("correctAnswer", parts[2]);
                        detailMap.put("isCorrect", Boolean.parseBoolean(parts[3]));
                        answerDetails.add(detailMap);
                    }
                }
            }
        }
        
        // Create analytics object
        RandomForestService.StudentAnalytics analytics = new RandomForestService.StudentAnalytics(
            studentId,
            submission.getTopicMastery(),
            submission.getDifficultyResilience(),
            submission.getAccuracy(),
            submission.getTimeEfficiency(),
            submission.getConfidence(),
            submission.getPerformanceCategory()
        );
        
        model.addAttribute("score", submission.getScore());
        model.addAttribute("total", submission.getTotalQuestions());
        model.addAttribute("percentage", submission.getPercentage());
        model.addAttribute("answerDetails", answerDetails);
        model.addAttribute("analytics", analytics);
        
        return "student-results";
    }
    
    @GetMapping("/view-exam/{submissionId}")
    public String viewExam(@PathVariable Long submissionId, Model model, java.security.Principal principal) {
        String studentId = principal.getName();
        
        Optional<ExamSubmission> submissionOpt = examSubmissionRepository.findById(submissionId);
        
        if (submissionOpt.isEmpty() || !submissionOpt.get().getStudentEmail().equals(studentId)) {
            model.addAttribute("error", "Submission not found");
            return "redirect:/student/dashboard";
        }
        
        ExamSubmission submission = submissionOpt.get();
        
        // Parse answer details to display questions and answers
        List<Map<String, Object>> answerDetails = new ArrayList<>();
        if (submission.getAnswerDetailsJson() != null && !submission.getAnswerDetailsJson().isEmpty()) {
            String[] details = submission.getAnswerDetailsJson().split(";");
            for (String detail : details) {
                if (!detail.trim().isEmpty()) {
                    String[] parts = detail.split("\\|");
                    if (parts.length == 4) {
                        Map<String, Object> detailMap = new HashMap<>();
                        detailMap.put("questionNumber", Integer.parseInt(parts[0]));
                        detailMap.put("studentAnswer", parts[1]);
                        detailMap.put("correctAnswer", parts[2]);
                        detailMap.put("isCorrect", Boolean.parseBoolean(parts[3]));
                        answerDetails.add(detailMap);
                    }
                }
            }
        }
        
        model.addAttribute("submission", submission);
        model.addAttribute("answerDetails", answerDetails);
        
        return "student-view-exam";
    }

    @GetMapping("/take-exam")
    public String takeExam(HttpSession session, Model model, java.security.Principal principal) {
        String studentId = principal.getName();
        List<String> exam = getDistributedExams().get(studentId);
        
        if (exam == null || exam.isEmpty()) {
            model.addAttribute("error", "No exam available for you yet.");
            return "redirect:/student/dashboard";
        }
        
        model.addAttribute("exam", exam);
        return "student-exam";
    }

    @PostMapping("/submit")
    public String submitExam(@RequestParam Map<String, String> answers, 
                            HttpSession session, Model model,
                            java.security.Principal principal) {
        String studentId = principal != null ? principal.getName() : "guest";
        
        // Get answer key for this student from the AnswerKeyService
        Map<Integer, String> key = answerKeyService.getStudentAnswerKey(studentId);
        
        // Fallback to session if not found in service (backward compatibility)
        if (key == null) {
            @SuppressWarnings("unchecked")
            Map<Integer, String> sessionKey = (Map<Integer, String>) session.getAttribute("correctAnswerKey");
            key = sessionKey;
        }
        
        // DEBUG: Console logging
        System.out.println("\n========== EXAM GRADING DEBUG ==========");
        System.out.println("Student: " + studentId);
        System.out.println("Answer Key Available: " + (key != null ? "YES" : "NO"));
        if (key != null) {
            System.out.println("Total Questions in Answer Key: " + key.size());
        }
        System.out.println("Student Submitted Answers: " + answers.size());
        System.out.println("----------------------------------------");
        
        // Convert answers to list and calculate score
        List<String> answerList = new ArrayList<>();
        List<Map<String, Object>> answerDetails = new ArrayList<>();
        int score = 0;
        double percentage = 0;
        RandomForestService.StudentAnalytics analytics = null;
        
        if (key != null) {
            for (int i = 1; i <= key.size(); i++) {
                String studentAns = answers.get("q" + i);
                String correctAns = key.get(i);
                answerList.add(studentAns != null ? studentAns : "");
                
                boolean isCorrect = studentAns != null && correctAns != null && 
                                   studentAns.trim().equalsIgnoreCase(correctAns.trim());
                
                if (isCorrect) {
                    score++;
                }
                
                // DEBUG: Console output
                System.out.println("Question " + i + ":");
                System.out.println("  Student Answer: '" + (studentAns != null ? studentAns.trim() : "NO ANSWER") + "'");
                System.out.println("  Correct Answer: '" + (correctAns != null ? correctAns.trim() : "NOT SET") + "'");
                System.out.println("  Result: " + (isCorrect ? "✓ CORRECT" : "✗ WRONG"));
                System.out.println();
                
                // Store details for displaying on results page (when released)
                Map<String, Object> detail = new HashMap<>();
                detail.put("questionNumber", i);
                detail.put("studentAnswer", studentAns != null ? studentAns.trim() : "No Answer");
                detail.put("correctAnswer", correctAns != null ? correctAns.trim() : "Not Set");
                detail.put("isCorrect", isCorrect);
                answerDetails.add(detail);
            }
            
            // Prevent division by zero
            if (key.size() > 0) {
                percentage = (score * 100.0 / key.size());
            } else {
                percentage = 0.0;
                System.out.println("WARNING: Answer key is empty (0 questions)!");
            }
            
            System.out.println("----------------------------------------");
            System.out.println("FINAL SCORE: " + score + " / " + key.size());
            System.out.println("PERCENTAGE: " + (key.size() > 0 ? "%.2f".formatted(percentage) + "%" : "N/A (no questions)"));
            System.out.println("========================================\n");
            
            // Calculate Random Forest Analytics
            analytics = randomForestService.calculateStudentAnalytics(studentId, answerList, key, null);
            
            // Calculate IRT 3PL Ability Estimate
            List<Boolean> responses = new ArrayList<>();
            for (String ans : answerList) {
                String correct = key.get(responses.size() + 1);
                responses.add(ans != null && correct != null && ans.trim().equalsIgnoreCase(correct.trim()));
            }
            
            List<IRT3PLService.ItemParameters> itemParams = irt3PLService.generateDefaultItemParameters(key.size());
            IRT3PLService.AbilityEstimate abilityEstimate = irt3PLService.estimateAbility(responses, itemParams);
            
            System.out.println("=== IRT 3PL Analysis ===");
            System.out.println("Estimated Ability (θ): " + String.format("%.3f", abilityEstimate.getTheta()));
            System.out.println("Standard Error: " + String.format("%.3f", abilityEstimate.getStandardError()));
            System.out.println("Scaled Score (500±100): " + irt3PLService.thetaToScaledScore(abilityEstimate.getTheta(), 500, 100));
            System.out.println("========================");
            
            // Store IRT metrics in session for display
            session.setAttribute("irtTheta", abilityEstimate.getTheta());
            session.setAttribute("irtScaledScore", irt3PLService.thetaToScaledScore(abilityEstimate.getTheta(), 500, 100));
            session.setAttribute("irtStandardError", abilityEstimate.getStandardError());
            
            // Save submission to database (auto-released)
            ExamSubmission submission = new ExamSubmission();
            submission.setStudentEmail(studentId);
            submission.setExamName("General Exam"); // You can make this dynamic
            submission.setScore(score);
            submission.setTotalQuestions(key.size());
            submission.setPercentage(percentage);
            submission.setResultsReleased(true); // AUTO-RELEASE: Both teacher and student can see
            submission.setSubmittedAt(LocalDateTime.now());
            submission.setReleasedAt(LocalDateTime.now()); // Released immediately
            
            // Store analytics (validate to prevent NaN values)
            if (analytics != null) {
                submission.setTopicMastery(validateDouble(analytics.getTopicMastery()));
                submission.setDifficultyResilience(validateDouble(analytics.getDifficultyResilience()));
                submission.setAccuracy(validateDouble(analytics.getAccuracy()));
                submission.setTimeEfficiency(validateDouble(analytics.getTimeEfficiency()));
                submission.setConfidence(validateDouble(analytics.getConfidence()));
                submission.setPerformanceCategory(analytics.getPerformanceCategory());
            }
            
            // Store answer details as simple string (can be JSON later)
            StringBuilder detailsStr = new StringBuilder();
            for (Map<String, Object> detail : answerDetails) {
                detailsStr.append(detail.get("questionNumber")).append("|")
                         .append(detail.get("studentAnswer")).append("|")
                         .append(detail.get("correctAnswer")).append("|")
                         .append(detail.get("isCorrect")).append(";");
            }
            submission.setAnswerDetailsJson(detailsStr.toString());
            
            examSubmissionRepository.save(submission);
            System.out.println("Submission saved to database. Results automatically available.");
            
        } else {
            System.out.println("ERROR: No answer key found for student " + studentId);
            System.out.println("========================================\n");
        }
        
        // Show results immediately (both teacher and student can see)
        model.addAttribute("score", score);
        model.addAttribute("total", key != null ? key.size() : 0);
        model.addAttribute("percentage", percentage);
        model.addAttribute("answerDetails", answerDetails);
        model.addAttribute("analytics", analytics);
        
        return "student-results";
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
    
    /**
     * API endpoint for fetching student analytics (for AJAX calls)
     */
    @PostMapping("/api/student-analytics")
    @ResponseBody
    public Map<String, Object> getStudentAnalytics(HttpSession session) {
        RandomForestService.StudentAnalytics analytics = 
            (RandomForestService.StudentAnalytics) session.getAttribute("studentAnalytics");
        
        Map<String, Object> response = new HashMap<>();
        
        if (analytics != null) {
            response.put("topicMastery", analytics.getTopicMastery());
            response.put("difficultyResilience", analytics.getDifficultyResilience());
            response.put("accuracy", analytics.getAccuracy());
            response.put("timeEfficiency", analytics.getTimeEfficiency());
            response.put("confidence", analytics.getConfidence());
            response.put("performanceCategory", analytics.getPerformanceCategory());
            
            // Add historical data
            List<RandomForestService.HistoricalPerformance> history = 
                randomForestService.getHistoricalData(analytics.getStudentId());
            
            List<String> dates = new ArrayList<>();
            List<Double> scores = new ArrayList<>();
            for (RandomForestService.HistoricalPerformance h : history) {
                dates.add(h.getExamName());
                scores.add(h.getScore());
            }
            
            Map<String, Object> historicalData = new HashMap<>();
            historicalData.put("dates", dates);
            historicalData.put("scores", scores);
            response.put("historicalData", historicalData);
        } else {
            // Return default values if no analytics available
            response.put("topicMastery", 0);
            response.put("difficultyResilience", 0);
            response.put("accuracy", 0);
            response.put("timeEfficiency", 0);
            response.put("confidence", 0);
            response.put("performanceCategory", "No Data");
        }
        
        return response;
    }
}
