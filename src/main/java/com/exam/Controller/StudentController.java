package com.exam.Controller;

import Service.RandomForestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.*;

@Controller
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private RandomForestService randomForestService;

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
        return "student-dashboard";
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
        Map<Integer, String> key = (Map<Integer, String>) session.getAttribute("correctAnswerKey");
        String studentId = principal != null ? principal.getName() : "guest";
        
        // Convert answers to list
        List<String> answerList = new ArrayList<>();
        int score = 0;
        
        if (key != null) {
            for (int i = 1; i <= key.size(); i++) {
                String studentAns = answers.get("q" + i);
                answerList.add(studentAns != null ? studentAns : "");
                
                if (studentAns != null && studentAns.equals(key.get(i))) {
                    score++;
                }
            }
            
            // Calculate Random Forest Analytics
            RandomForestService.StudentAnalytics analytics = 
                randomForestService.calculateStudentAnalytics(studentId, answerList, key, null);
            
            model.addAttribute("analytics", analytics);
            
            // Store analytics in session for later retrieval
            session.setAttribute("studentAnalytics", analytics);
        }
        
        model.addAttribute("score", score);
        model.addAttribute("total", key != null ? key.size() : 0);
        return "student-results";
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
