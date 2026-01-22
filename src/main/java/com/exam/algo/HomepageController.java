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