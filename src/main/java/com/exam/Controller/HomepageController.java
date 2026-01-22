package com.exam.Controller;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfWriter;

import Service.RandomForestService;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.awt.Color;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomepageController {

    @Autowired
    private RandomForestService randomForestService;

    private static List<String> enrolledStudents = new ArrayList<>();
    private static Map<String, List<String>> distributedExams = new HashMap<>();

    @GetMapping("/homepage")
    public String showHomepage(Model model) {
        model.addAttribute("enrolledStudents", enrolledStudents);
        return "homepage";
    }

    @PostMapping("/enroll-student")
    public String enrollStudent(@RequestParam String studentId) {
        if (!enrolledStudents.contains(studentId)) {
            enrolledStudents.add(studentId);
        }
        return "redirect:/homepage";
    }

    @PostMapping("/distribute")
    public String distributeExam(@RequestParam String targetStudent, HttpSession session) {
        List<String> exam = (List<String>) session.getAttribute("shuffledExam");
        if (exam != null) {
            distributedExams.put(targetStudent, exam);
        }
        return "redirect:/homepage";
    }

    @PostMapping("/process-exams")
    public String processExams(@RequestParam(value = "examCreated", required = false) MultipartFile examCreated,
                               HttpSession session, Model model) throws Exception {
        if (examCreated != null && !examCreated.isEmpty()) {
            List<String> randomizedLines = processFisherYates(examCreated, session);
            session.setAttribute("shuffledExam", randomizedLines);
            model.addAttribute("message", "Exam Randomized. Ready for distribution.");
            model.addAttribute("type", "exam");
        }
        return "results";
    }

    @GetMapping("/student/dashboard")
    public String studentDashboard(HttpSession session, Model model, java.security.Principal principal) {
        String studentId = principal.getName();
        model.addAttribute("hasExam", distributedExams.containsKey(studentId));
        return "student-dashboard";
    }

    @GetMapping("/student/take-exam")
    public String takeExam(HttpSession session, Model model, java.security.Principal principal) {
        String studentId = principal.getName();
        model.addAttribute("exam", distributedExams.get(studentId));
        return "student-exam";
    }

    @PostMapping("/student/submit")
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

    private List<String> processFisherYates(MultipartFile file, HttpSession session) throws IOException {
        List<String> rawLines = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            rawLines = Arrays.stream(stripper.getText(document).split("\\r?\\n"))
                             .filter(line -> !line.trim().isEmpty()).collect(Collectors.toList());
        }

        List<String> questionBlocks = new ArrayList<>();
        Map<Integer, String> answerKey = new HashMap<>();
        StringBuilder currentBlock = new StringBuilder();
        SecureRandom rand = new SecureRandom();
        int qID = 0;

        for (String line : rawLines) {
            String trimmed = line.trim();

            // Strict Noise Filter: Ignore page numbers, standalone quiz titles, and empty headers
            if (trimmed.toLowerCase().contains("page") || 
                trimmed.equalsIgnoreCase("General Knowledge Quiz") ||
                trimmed.equalsIgnoreCase("NAME:") || 
                trimmed.equalsIgnoreCase("DATE:")) continue;

            // Strict Question Detection: Matches "1. 1.", "2. 6.", etc.
            if (trimmed.matches("^\\d+\\.\\s*\\d+\\..*")) {
                if (currentBlock.length() > 0) {
                    String processed = extractAnswerAndShuffle(currentBlock.toString(), rand, answerKey, ++qID);
                    if (!processed.isEmpty()) questionBlocks.add(processed);
                }
                // Strip the double numbering (e.g. "1. 1. ")
                currentBlock = new StringBuilder(trimmed.replaceFirst("^(\\d+\\.\\s*\\d+\\.\\s*)", ""));
            } else {
                currentBlock.append("\n").append(trimmed);
            }
        }
        
        if (currentBlock.length() > 0) {
            String processed = extractAnswerAndShuffle(currentBlock.toString(), rand, answerKey, ++qID);
            if (!processed.isEmpty()) questionBlocks.add(processed);
        }

        session.setAttribute("correctAnswerKey", answerKey);
        Collections.shuffle(questionBlocks, rand);
        return questionBlocks;
    }

    private String extractAnswerAndShuffle(String block, SecureRandom rand, Map<Integer, String> key, int id) {
        String[] lines = block.split("\n");
        if (lines.length <= 1) return ""; // Guard against blocks without choices

        String questionText = lines[0];
        List<String> choices = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].toLowerCase().startsWith("answer:")) {
                String ans = lines[i].replaceFirst("(?i)answer:\\s*", "").trim();
                if (!ans.isEmpty()) key.put(id, ans);
            } else {
                choices.add(lines[i]);
            }
        }

        if (choices.isEmpty()) return "";
        Collections.shuffle(choices, rand);
        return questionText + "\n" + String.join("\n", choices);
    }
}