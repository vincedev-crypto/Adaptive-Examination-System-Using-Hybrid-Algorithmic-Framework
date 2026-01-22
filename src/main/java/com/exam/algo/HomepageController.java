package com.exam.algo;

import com.exam.entity.EnrolledStudent;
import com.exam.entity.ExamSubmission;
import com.exam.entity.User;
import com.exam.repository.EnrolledStudentRepository;
import com.exam.repository.ExamSubmissionRepository;
import com.exam.repository.UserRepository;
import com.exam.service.AnswerKeyService;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teacher")
public class HomepageController {

    @Autowired
    private AnswerKeyService answerKeyService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private EnrolledStudentRepository enrolledStudentRepository;
    
    @Autowired
    private ExamSubmissionRepository examSubmissionRepository;

    private static Map<String, List<String>> distributedExams = new HashMap<>();

    // Public getter for accessing distributed exams from other controllers
    public static Map<String, List<String>> getDistributedExams() {
        return distributedExams;
    }
    
    // Inner class for Question
    private static class Question {
        int id;
        String text;
        List<String> choices;
        
        Question(int id, String text) {
            this.id = id;
            this.text = text;
            this.choices = new ArrayList<>();
        }
    }

    @GetMapping("/homepage")
    public String showHomepage(Model model, java.security.Principal principal) {
        String teacherEmail = principal != null ? principal.getName() : "Teacher";
        
        // Fetch all students from database
        List<User> allStudents = userRepository.findAll().stream()
            .filter(user -> user.getRole() == User.Role.STUDENT)
            .collect(Collectors.toList());
        
        // Fetch enrolled students for this teacher
        List<EnrolledStudent> enrolledStudents = enrolledStudentRepository.findByTeacherEmail(teacherEmail);
        
        // Fetch exam submissions
        List<ExamSubmission> submissions = examSubmissionRepository.findAll();
        
        model.addAttribute("teacherEmail", teacherEmail);
        model.addAttribute("enrolledStudents", enrolledStudents);
        model.addAttribute("allStudents", allStudents);
        model.addAttribute("submissions", submissions);
        return "homepage";
    }

    @PostMapping("/enroll-student")
    public String enrollStudent(@RequestParam String studentEmail, java.security.Principal principal) {
        String teacherEmail = principal.getName();
        
        // Check if already enrolled
        Optional<EnrolledStudent> existing = enrolledStudentRepository
            .findByTeacherEmailAndStudentEmail(teacherEmail, studentEmail);
        
        if (existing.isEmpty()) {
            // Get student name
            Optional<User> studentOpt = userRepository.findByEmail(studentEmail);
            if (studentOpt.isPresent()) {
                EnrolledStudent enrollment = new EnrolledStudent(
                    teacherEmail,
                    studentEmail,
                    studentOpt.get().getFullName()
                );
                enrolledStudentRepository.save(enrollment);
            }
        }
        
        return "redirect:/teacher/homepage";
    }
    
    @PostMapping("/remove-student")
    @Transactional
    public String removeStudent(@RequestParam Long enrollmentId) {
        enrolledStudentRepository.deleteById(enrollmentId);
        return "redirect:/teacher/homepage";
    }

    @PostMapping("/distribute")
    public String distributeExam(@RequestParam String targetStudent, HttpSession session) {
        @SuppressWarnings("unchecked")
        List<String> exam = (List<String>) session.getAttribute("shuffledExam");
        
        @SuppressWarnings("unchecked")
        Map<Integer, String> answerKey = (Map<Integer, String>) session.getAttribute("correctAnswerKey");
        
        if (exam != null) {
            distributedExams.put(targetStudent, exam);
            
            // Store the answer key for this student so we can grade their exam later
            if (answerKey != null) {
                answerKeyService.storeStudentAnswerKey(targetStudent, answerKey);
            }
        }
        return "redirect:/teacher/homepage";
    }

    @PostMapping("/process-exams")
    public String processExams(@RequestParam(value = "examCreated", required = false) MultipartFile examCreated,
                               @RequestParam(value = "answerKeyPdf", required = false) MultipartFile answerKeyPdf,
                               HttpSession session, Model model) throws Exception {
        if (examCreated != null && !examCreated.isEmpty()) {
            Map<Integer, String> answerKey = new HashMap<>();
            
            // Check if separate answer key PDF is provided
            if (answerKeyPdf != null && !answerKeyPdf.isEmpty()) {
                // Parse separate answer key PDF
                answerKey = parseAnswerKeyPdf(answerKeyPdf);
                model.addAttribute("message", "Exam and Answer Key processed successfully!");
            }
            
            // Process exam (with or without embedded answers)
            List<String> randomizedLines = processFisherYates(examCreated, session, answerKey);
            session.setAttribute("shuffledExam", randomizedLines);
            model.addAttribute("type", "exam");
        }
        return "results";
    }
    
    /**
     * Parse a separate answer key PDF
     * Expected format:
     * 1. Mars
     * 2. Leonardo da Vinci
     * 3. Gold
     * OR
     * Question 1: Mars
     * Question 2: Leonardo da Vinci
     */
    private Map<Integer, String> parseAnswerKeyPdf(MultipartFile file) throws IOException {
        Map<Integer, String> answerKey = new HashMap<>();
        List<String> lines = new ArrayList<>();
        
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            lines = Arrays.stream(stripper.getText(document).split("\\r?\\n"))
                         .filter(line -> !line.trim().isEmpty())
                         .collect(Collectors.toList());
        }
        
        System.out.println("=== PARSING ANSWER KEY PDF ===");
        System.out.println("Total lines: " + lines.size());
        
        // Improved skipPattern: Skip metadata, headers, page numbers, etc.
        Pattern skipPattern = Pattern.compile(
            "(?i)(page\\s*\\d+|examination\\s+paper|name:|date:|answer\\s+key|confidential|date\\s+generated|instructions?|total\\s+marks)", 
            Pattern.CASE_INSENSITIVE
        );
        
        int questionNumber = 1;
        for (String line : lines) {
            String trimmed = line.trim();
            
            // Skip headers/metadata
            if (skipPattern.matcher(trimmed).find()) {
                System.out.println("Skipping header/metadata: " + trimmed);
                continue;
            }
            
            // Skip very short lines that are likely metadata
            if (trimmed.length() < 2) continue;
            
            String answer = null;
            int qNum = 0;
            
            // Format 1: "1. Mars" or "1) Mars"
            if (trimmed.matches("^\\d+[\\.\\)]\\s+.+")) {
                String[] parts = trimmed.split("[\\.\\)]", 2);
                if (parts.length == 2) {
                    qNum = Integer.parseInt(parts[0].trim());
                    answer = parts[1].trim();
                }
            }
            // Format 2: "Question 1: Mars" or "Q1: Mars"
            else if (trimmed.matches("(?i)^(question|q)\\s*\\d+\\s*:\\s*.+")) {
                String[] parts = trimmed.split(":", 2);
                if (parts.length == 2) {
                    String numPart = parts[0].replaceAll("[^0-9]", "");
                    qNum = Integer.parseInt(numPart);
                    answer = parts[1].trim();
                }
            }
            // Format 3: Just answers listed line by line (1st line = Q1, 2nd = Q2, etc.)
            else if (!trimmed.matches("^\\d+$")) { // Not just a number
                qNum = questionNumber++;
                answer = trimmed;
            }
            
            // Clean up the answer: remove choice letter prefixes like "a) ", "b) ", "c) "
            if (answer != null) {
                answer = answer.replaceFirst("^[A-Da-d]\\)\\s*", "").trim();
                
                // Double-check it's not a header that slipped through
                if (!answer.isEmpty() && !skipPattern.matcher(answer).find()) {
                    answerKey.put(qNum, answer);
                    System.out.println("Parsed Q" + qNum + " -> " + answer);
                }
            }
        }
        
        System.out.println("=== ANSWER KEY PARSED: " + answerKey.size() + " answers ===");
        if (answerKey.isEmpty()) {
            System.out.println("WARNING: No answers were extracted from the answer key PDF!");
        }
        
        return answerKey;
    }

    private List<String> processFisherYates(MultipartFile file, HttpSession session, 
                                           Map<Integer, String> externalAnswerKey) throws IOException {
        List<String> rawLines = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            rawLines = Arrays.stream(stripper.getText(document).split("\\r?\\n"))
                             .filter(line -> !line.trim().isEmpty()).collect(Collectors.toList());
        }

        System.out.println("=== PROCESSING EXAM PDF ===");
        System.out.println("Total lines: " + rawLines.size());
        
        // Improved skipPattern: Skip metadata, headers, page numbers, etc.
        Pattern skipPattern = Pattern.compile(
            "(?i)(page\\s*\\d+|examination\\s+paper|name:|date:|confidential|date\\s+generated|instructions?|total\\s+marks)", 
            Pattern.CASE_INSENSITIVE
        );

        List<String> questionBlocks = new ArrayList<>();
        Map<Integer, String> answerKey = new HashMap<>();
        StringBuilder currentBlock = new StringBuilder();
        SecureRandom rand = new SecureRandom();
        int qID = 0;

        for (String line : rawLines) {
            String trimmed = line.trim();

            // Skip headers, page numbers, and metadata using improved pattern
            if (skipPattern.matcher(trimmed).find() || trimmed.isEmpty()) {
                System.out.println("Skipping: " + trimmed);
                continue;
            }

            // Detect new question: starts with number followed by period (e.g., "1.", "2.", "10.")
            if (trimmed.matches("^\\d+\\.\\s+.*")) {
                // Save previous question block if exists
                if (currentBlock.length() > 0) {
                    String processed = extractAnswerAndShuffle(currentBlock.toString(), rand, answerKey, qID);
                    if (!processed.isEmpty()) {
                        questionBlocks.add(processed);
                        System.out.println("Processed Q" + (qID + 1));
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
                System.out.println("Processed Q" + (qID + 1));
                qID++;
            }
        }

        System.out.println("=== EXAM PARSED: " + questionBlocks.size() + " questions ===");

        // Merge external answer key if provided (external answers override embedded ones)
        if (externalAnswerKey != null && !externalAnswerKey.isEmpty()) {
            System.out.println("Using external answer key with " + externalAnswerKey.size() + " answers");
            answerKey.putAll(externalAnswerKey);
        }
        
        // Print final answer key for debugging
        System.out.println("=== FINAL ANSWER KEY ===");
        for (int i = 1; i <= Math.max(questionBlocks.size(), answerKey.size()); i++) {
            String answer = answerKey.get(i);
            if (answer != null) {
                System.out.println("Q" + i + " -> " + answer);
            } else {
                System.out.println("Q" + i + " -> NO ANSWER FOUND!");
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
                // Remove choice prefix if present
                correctAnswer = correctAnswer.replaceFirst("^[A-Da-d]\\)\\s*", "").trim();
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

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPDF(HttpSession session) throws DocumentException, IOException {
        @SuppressWarnings("unchecked")
        List<String> exam = (List<String>) session.getAttribute("shuffledExam");
        
        if (exam == null || exam.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, baos);
        document.open();

        // Add title
        Paragraph title = new Paragraph("EXAMINATION PAPER");
        title.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("NAME: _______________________     DATE: ___________"));
        document.add(new Paragraph("\n\n"));

        // Add questions
        int questionNumber = 1;
        for (String question : exam) {
            document.add(new Paragraph(questionNumber + ". " + question.replace("\n", "\n   ")));
            document.add(new Paragraph("\n"));
            questionNumber++;
        }

        document.close();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "exam_" + System.currentTimeMillis() + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(baos.toByteArray());
    }

    @GetMapping("/export/word")
    public ResponseEntity<byte[]> exportWord(HttpSession session) throws IOException {
        @SuppressWarnings("unchecked")
        List<String> exam = (List<String>) session.getAttribute("shuffledExam");
        
        if (exam == null || exam.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XWPFDocument document = new XWPFDocument();

        // Add title
        XWPFParagraph titlePara = document.createParagraph();
        titlePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText("EXAMINATION PAPER");
        titleRun.setBold(true);
        titleRun.setFontSize(18);

        // Add header info
        XWPFParagraph headerPara = document.createParagraph();
        XWPFRun headerRun = headerPara.createRun();
        headerRun.addBreak();
        headerRun.setText("NAME: _______________________     DATE: ___________");
        headerRun.addBreak();
        headerRun.addBreak();

        // Add questions
        int questionNumber = 1;
        for (String question : exam) {
            XWPFParagraph questionPara = document.createParagraph();
            XWPFRun questionRun = questionPara.createRun();
            
            String[] lines = question.split("\n");
            questionRun.setText(questionNumber + ". " + lines[0]);
            questionRun.addBreak();
            
            for (int i = 1; i < lines.length; i++) {
                questionRun.setText("   " + lines[i]);
                questionRun.addBreak();
            }
            
            questionRun.addBreak();
            questionNumber++;
        }

        document.write(baos);
        document.close();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDispositionFormData("attachment", "exam_" + System.currentTimeMillis() + ".docx");

        return ResponseEntity.ok()
                .headers(headers)
                .body(baos.toByteArray());
    }

    @GetMapping("/export/answer-key")
    public ResponseEntity<byte[]> exportAnswerKey(HttpSession session) throws DocumentException, IOException {
        @SuppressWarnings("unchecked")
        Map<Integer, String> answerKey = (Map<Integer, String>) session.getAttribute("correctAnswerKey");
        
        if (answerKey == null || answerKey.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, baos);
        document.open();

        // Add title
        Paragraph title = new Paragraph("ANSWER KEY - CONFIDENTIAL");
        title.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("Date Generated: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())));
        document.add(new Paragraph("\n\n"));

        // Add answers in order
        List<Integer> sortedKeys = new ArrayList<>(answerKey.keySet());
        Collections.sort(sortedKeys);
        
        for (Integer questionNum : sortedKeys) {
            String answer = answerKey.get(questionNum);
            document.add(new Paragraph("Question " + questionNum + ": " + answer));
        }

        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("Total Questions: " + answerKey.size()));

        document.close();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "answer_key_" + System.currentTimeMillis() + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(baos.toByteArray());
    }
    
    /**
     * Public method to get the AnswerKeyService for use by other controllers
     */
    public AnswerKeyService getAnswerKeyService() {
        return answerKeyService;
    }
}