package com.exam.algo;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teacher")
public class HomepageController {

    private static List<String> enrolledStudents = new ArrayList<>();
    private static Map<String, List<String>> distributedExams = new HashMap<>();

    // Public getter for accessing distributed exams from other controllers
    public static Map<String, List<String>> getDistributedExams() {
        return distributedExams;
    }

    @GetMapping("/homepage")
    public String showHomepage(Model model, java.security.Principal principal) {
        String teacherEmail = principal != null ? principal.getName() : "Teacher";
        model.addAttribute("teacherEmail", teacherEmail);
        model.addAttribute("enrolledStudents", enrolledStudents);
        return "homepage";
    }

    @PostMapping("/enroll-student")
    public String enrollStudent(@RequestParam String studentId) {
        if (!enrolledStudents.contains(studentId)) {
            enrolledStudents.add(studentId);
        }
        return "redirect:/teacher/homepage";
    }

    @PostMapping("/distribute")
    public String distributeExam(@RequestParam String targetStudent, HttpSession session) {
        List<String> exam = (List<String>) session.getAttribute("shuffledExam");
        if (exam != null) {
            distributedExams.put(targetStudent, exam);
        }
        return "redirect:/teacher/homepage";
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

            // Skip headers, page numbers, and metadata
            if (trimmed.toLowerCase().contains("page") || 
                trimmed.toLowerCase().contains("examination paper") ||
                trimmed.toUpperCase().startsWith("NAME:") || 
                trimmed.toUpperCase().startsWith("DATE:") ||
                trimmed.isEmpty()) {
                continue;
            }

            // Detect new question: starts with number followed by period (e.g., "1.", "2.", "10.")
            if (trimmed.matches("^\\d+\\.\\s+.*")) {
                // Save previous question block if exists
                if (currentBlock.length() > 0) {
                    String processed = extractAnswerAndShuffle(currentBlock.toString(), rand, answerKey, qID);
                    if (!processed.isEmpty()) {
                        questionBlocks.add(processed);
                        qID++;
                    }
                }
                // Start new question block (remove the number prefix like "1. ")
                currentBlock = new StringBuilder(trimmed.replaceFirst("^\\d+\\.\\s+", ""));
            } else {
                // Add to current question block
                if (currentBlock.length() > 0) {
                    currentBlock.append("\n").append(trimmed);
                }
            }
        }
        
        // Don't forget the last question
        if (currentBlock.length() > 0) {
            String processed = extractAnswerAndShuffle(currentBlock.toString(), rand, answerKey, qID);
            if (!processed.isEmpty()) {
                questionBlocks.add(processed);
                qID++;
            }
        }

        session.setAttribute("correctAnswerKey", answerKey);
        
        // Shuffle the order of questions
        Collections.shuffle(questionBlocks, rand);
        
        return questionBlocks;
    }

    private String extractAnswerAndShuffle(String block, SecureRandom rand, Map<Integer, String> key, int id) {
        String[] lines = block.split("\n");
        if (lines.length <= 1) return ""; // Need at least question + 1 choice

        String questionText = lines[0].trim();
        List<String> choices = new ArrayList<>();
        String correctAnswer = null;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // Check if this line indicates the correct answer
            if (line.toLowerCase().startsWith("answer:") || 
                line.toLowerCase().startsWith("correct:") ||
                line.toLowerCase().startsWith("correct answer:")) {
                // Extract the answer (e.g., "Answer: A" or "Answer: Paris")
                correctAnswer = line.replaceFirst("(?i)(answer|correct|correct answer):\\s*", "").trim();
                continue;
            }
            
            // Check if it's an answer choice (A), B), C), D) or just add as choice
            if (!line.isEmpty() && !line.equalsIgnoreCase("choices:") && !line.equalsIgnoreCase("options:")) {
                // Remove choice labels like "A)", "B)", etc. for storage
                String cleanedChoice = line.replaceFirst("^[A-Za-z]\\)\\s*", "").trim();
                if (!cleanedChoice.isEmpty()) {
                    choices.add(cleanedChoice);
                    
                    // If this choice matches the correct answer, remember it
                    if (correctAnswer != null && 
                        (line.startsWith(correctAnswer + ")") || cleanedChoice.equalsIgnoreCase(correctAnswer))) {
                        key.put(id + 1, cleanedChoice); // Store as cleaned choice
                    }
                }
            }
        }

        // If no choices found, skip this question
        if (choices.isEmpty()) return "";
        
        // If correct answer was just a letter like "A", "B", etc., we already stored it above
        // Otherwise store the literal answer text
        if (correctAnswer != null && !key.containsKey(id + 1)) {
            key.put(id + 1, correctAnswer);
        }

        // Shuffle the answer choices (Fisher-Yates randomization)
        Collections.shuffle(choices, rand);
        
        // Format: Question text followed by shuffled choices with new labels
        StringBuilder result = new StringBuilder(questionText);
        char label = 'A';
        for (String choice : choices) {
            result.append("\n").append(label).append(") ").append(choice);
            label++;
        }
        
        return result.toString();
    }
}