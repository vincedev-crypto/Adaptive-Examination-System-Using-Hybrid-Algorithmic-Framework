package com.exam.algo;

import com.exam.entity.EnrolledStudent;
import com.exam.entity.ExamSubmission;
import com.exam.entity.User;
import com.exam.repository.EnrolledStudentRepository;
import com.exam.repository.ExamSubmissionRepository;
import com.exam.repository.UserRepository;
import com.exam.service.AnswerKeyService;
import com.exam.service.FisherYatesService;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teacher")
public class HomepageController {

    @Autowired
    private AnswerKeyService answerKeyService;
    
    @Autowired
    private FisherYatesService fisherYatesService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private EnrolledStudentRepository enrolledStudentRepository;
    
    @Autowired
    private ExamSubmissionRepository examSubmissionRepository;

    private static Map<String, List<String>> distributedExams = new HashMap<>();
    
    // Store uploaded exams with their metadata
    private static Map<String, UploadedExam> uploadedExams = new HashMap<>();
    
    // Helper class to store exam metadata
    public static class UploadedExam {
        private String examId;
        private String examName;
        private String subject;
        private String activityType;
        private List<String> questions;
        private Map<Integer, String> answerKey;
        private java.time.LocalDateTime uploadedAt;
        
        public UploadedExam(String examId, String examName, String subject, String activityType, 
                          List<String> questions, Map<Integer, String> answerKey) {
            this.examId = examId;
            this.examName = examName;
            this.subject = subject;
            this.activityType = activityType;
            this.questions = questions;
            this.answerKey = answerKey;
            this.uploadedAt = java.time.LocalDateTime.now();
        }
        
        public String getExamId() { return examId; }
        public String getExamName() { return examName; }
        public String getSubject() { return subject; }
        public String getActivityType() { return activityType; }
        public List<String> getQuestions() { return questions; }
        public Map<Integer, String> getAnswerKey() { return answerKey; }
        public java.time.LocalDateTime getUploadedAt() { return uploadedAt; }
    }
    
    // Helper class for shuffling questions while preserving answer associations
    private static class QuestionWithAnswer {
        String question;
        String answer;
        int originalNumber;
        
        QuestionWithAnswer(String question, String answer, int originalNumber) {
            this.question = question;
            this.answer = answer;
            this.originalNumber = originalNumber;
        }
    }

    // Public getter for accessing distributed exams from other controllers
    public static Map<String, List<String>> getDistributedExams() {
        return distributedExams;
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
        model.addAttribute("uploadedExams", new ArrayList<>(uploadedExams.values()));
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
    public String distributeExam(@RequestParam String targetStudent, 
                                 @RequestParam String examId,
                                 @RequestParam Integer timeLimit,
                                 @RequestParam String deadline,
                                 HttpSession session) {
        UploadedExam selectedExam = uploadedExams.get(examId);
        
        if (selectedExam != null) {
            // Create a fresh copy of questions for this student
            List<String> studentExamCopy = new ArrayList<>(selectedExam.getQuestions());
            
            // Re-shuffle answer choices for each question block to make it unique per student
            List<String> uniqueExam = new ArrayList<>();
            SecureRandom rand = new SecureRandom();
            
            for (String questionBlock : studentExamCopy) {
                uniqueExam.add(reshuffleQuestionChoices(questionBlock, rand));
            }
            
            // Store the uniquely shuffled exam for this student
            distributedExams.put(targetStudent, uniqueExam);
            
            // Generate difficulty levels for each question
            List<String> difficultyLevels = generateQuestionDifficulties(uniqueExam.size(), rand);
            session.setAttribute("questionDifficulties_" + targetStudent, difficultyLevels);
            
            // Store exam metadata for student display
            session.setAttribute("examSubject_" + targetStudent, selectedExam.getSubject());
            session.setAttribute("examActivityType_" + targetStudent, selectedExam.getActivityType());
            session.setAttribute("examName_" + targetStudent, selectedExam.getExamName());
            
            // Store time limit and deadline
            session.setAttribute("examTimeLimit_" + targetStudent, timeLimit);
            session.setAttribute("examDeadline_" + targetStudent, deadline);
            session.setAttribute("examStartTime_" + targetStudent, java.time.LocalDateTime.now().toString());
            
            // Store the answer key for this student (answers remain the same, just choice order changes)
            if (selectedExam.getAnswerKey() != null) {
                answerKeyService.storeStudentAnswerKey(targetStudent, selectedExam.getAnswerKey());
            }
            
            System.out.println("Distributed unique shuffled exam to: " + targetStudent);
            System.out.println("Time limit: " + timeLimit + " minutes, Deadline: " + deadline);
        }
        return "redirect:/teacher/homepage";
    }
    
    /**
     * Generate difficulty levels for questions based on IRT-inspired distribution
     * Difficulty spread: 30% Easy, 50% Medium, 20% Hard
     */
    private List<String> generateQuestionDifficulties(int numQuestions, SecureRandom rand) {
        List<String> difficulties = new ArrayList<>();
        
        // Calculate distribution
        int numEasy = (int) Math.ceil(numQuestions * 0.30);
        int numHard = (int) Math.ceil(numQuestions * 0.20);
        int numMedium = numQuestions - numEasy - numHard;
        
        // Create difficulty pool
        for (int i = 0; i < numEasy; i++) difficulties.add("Easy");
        for (int i = 0; i < numMedium; i++) difficulties.add("Medium");
        for (int i = 0; i < numHard; i++) difficulties.add("Hard");
        
        // Shuffle to randomize difficulty order
        Collections.shuffle(difficulties, rand);
        
        return difficulties;
    }
    
    /**
     * Re-shuffle the answer choices within a question block to create unique exams
     */
    private String reshuffleQuestionChoices(String questionBlock, SecureRandom rand) {
        String[] lines = questionBlock.split("\n");
        if (lines.length <= 1) return questionBlock; // No choices to shuffle
        
        String questionText = lines[0];
        List<String> choices = new ArrayList<>();
        
        // Extract choices (lines with A), B), C), D) format)
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.matches("^[A-Za-z]\\)\\s+.+")) {
                // Remove the label and extract just the choice text
                String choiceText = line.replaceFirst("^[A-Za-z]\\)\\s+", "");
                choices.add(choiceText);
            }
        }
        
        // If no choices found, return original
        if (choices.isEmpty()) return questionBlock;
        
        // Shuffle the choices using Fisher-Yates
        fisherYatesService.shuffle(choices, rand);
        
        // Rebuild the question with reshuffled choices
        StringBuilder result = new StringBuilder(questionText);
        char label = 'A';
        for (String choice : choices) {
            result.append("\n").append(label).append(") ").append(choice);
            label++;
        }
        
        return result.toString();
    }

    @PostMapping("/process-exams")
    public String processExams(@RequestParam(value = "examCreated", required = false) MultipartFile examCreated,
                               @RequestParam(value = "answerKeyPdf", required = false) MultipartFile answerKeyPdf,
                               @RequestParam(value = "subject", required = false) String subject,
                               @RequestParam(value = "activityType", required = false) String activityType,
                               HttpSession session, Model model) throws Exception {
        if (examCreated != null && !examCreated.isEmpty()) {
            Map<Integer, String> answerKey = new HashMap<>();
            String fileName = examCreated.getOriginalFilename();
            boolean isCsvFormat = fileName != null && fileName.toLowerCase().endsWith(".csv");
            
            // Check if separate answer key is provided
            if (answerKeyPdf != null && !answerKeyPdf.isEmpty()) {
                String answerKeyFileName = answerKeyPdf.getOriginalFilename();
                boolean isAnswerKeyCsv = answerKeyFileName != null && answerKeyFileName.toLowerCase().endsWith(".csv");
                
                if (isAnswerKeyCsv) {
                    answerKey = parseAnswerKeyCsv(answerKeyPdf);
                } else {
                    answerKey = parseAnswerKeyPdf(answerKeyPdf);
                }
                model.addAttribute("message", "Exam and Answer Key processed successfully!");
            }
            
            // Process exam based on file type
            List<String> randomizedLines;
            if (isCsvFormat) {
                randomizedLines = processCsvExam(examCreated, session, answerKey);
            } else {
                randomizedLines = processFisherYates(examCreated, session, answerKey);
            }
            session.setAttribute("shuffledExam", randomizedLines);
            
            @SuppressWarnings("unchecked")
            Map<Integer, String> finalAnswerKey = (Map<Integer, String>) session.getAttribute("correctAnswerKey");
            
            // Store the uploaded exam for later selection
            String examId = "EXAM_" + System.currentTimeMillis();
            String examName = examCreated.getOriginalFilename().replace(".pdf", "");
            String examSubject = (subject != null && !subject.isEmpty()) ? subject : "General";
            String examActivityType = (activityType != null && !activityType.isEmpty()) ? activityType : "Exam";
            
            UploadedExam uploadedExam = new UploadedExam(examId, examName, examSubject, examActivityType, 
                                                         randomizedLines, finalAnswerKey);
            uploadedExams.put(examId, uploadedExam);
            
            model.addAttribute("type", "exam");
            model.addAttribute("examUploaded", true);
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
            "(?i)(page\\s*\\d+|examination\\s+paper|name:|date:|answer\\s+key|confidential|date\\s+generated|instructions?|total\\s+marks|student\\s+name:|id\\s+number:)", 
            Pattern.CASE_INSENSITIVE
        );
        
        // Patterns to detect question number formats
        Pattern questionPattern1 = Pattern.compile("^\\d+[\\.\\)]\\s+.+"); // "1. Answer" or "1) Answer"
        Pattern questionPattern2 = Pattern.compile("(?i)^(question|q)\\s*\\d+\\s*:\\s*.+"); // "Q1: Answer" or "Question 1: Answer"
        
        int lastQuestionNumber = 0;
        StringBuilder currentAnswer = new StringBuilder();
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            // Skip headers/metadata
            if (skipPattern.matcher(trimmed).find()) {
                System.out.println("Skipping header/metadata: " + trimmed);
                continue;
            }
            
            // Skip very short lines that are likely metadata
            if (trimmed.length() < 2) continue;
            
            // Skip lines that are just numbers
            if (trimmed.matches("^\\d+$")) continue;
            
            String answer = null;
            int qNum = 0;
            boolean isNewQuestion = false;
            
            // Format 1: "1. Mars" or "1) Mars"
            if (questionPattern1.matcher(trimmed).matches()) {
                String[] parts = trimmed.split("[\\.\\)]", 2);
                if (parts.length == 2) {
                    qNum = Integer.parseInt(parts[0].trim());
                    answer = parts[1].trim();
                    isNewQuestion = true;
                }
            }
            // Format 2: "Question 1: Mars" or "Q1: Mars"
            else if (questionPattern2.matcher(trimmed).matches()) {
                String[] parts = trimmed.split(":", 2);
                if (parts.length == 2) {
                    String numPart = parts[0].replaceAll("[^0-9]", "");
                    qNum = Integer.parseInt(numPart);
                    answer = parts[1].trim();
                    isNewQuestion = true;
                }
            }
            // This might be a continuation of the previous answer
            else if (lastQuestionNumber > 0) {
                // Append to the current answer with a space
                currentAnswer.append(" ").append(trimmed);
                // Update the existing entry
                String fullAnswer = currentAnswer.toString().trim();
                answerKey.put(lastQuestionNumber, fullAnswer);
                System.out.println("Appending to Q" + lastQuestionNumber + " -> " + fullAnswer);
                continue;
            }
            
            // If we found a new question
            if (isNewQuestion && answer != null) {
                // Save the previous question if exists
                if (lastQuestionNumber > 0 && currentAnswer.length() > 0) {
                    String finalAnswer = currentAnswer.toString().trim()
                        .replaceFirst("^[A-Da-d]\\)\\s*", "").trim();
                    if (!finalAnswer.isEmpty() && !skipPattern.matcher(finalAnswer).find()) {
                        answerKey.put(lastQuestionNumber, finalAnswer);
                        System.out.println("Completed Q" + lastQuestionNumber + " -> " + finalAnswer);
                    }
                }
                
                // Start new answer
                lastQuestionNumber = qNum;
                currentAnswer = new StringBuilder(answer.replaceFirst("^[A-Da-d]\\)\\s*", "").trim());
            }
        }
        
        // Don't forget the last question
        if (lastQuestionNumber > 0 && currentAnswer.length() > 0) {
            String finalAnswer = currentAnswer.toString().trim()
                .replaceFirst("^[A-Da-d]\\)\\s*", "").trim();
            if (!finalAnswer.isEmpty() && !skipPattern.matcher(finalAnswer).find()) {
                answerKey.put(lastQuestionNumber, finalAnswer);
                System.out.println("Completed Q" + lastQuestionNumber + " -> " + finalAnswer);
            }
        }
        
        System.out.println("=== ANSWER KEY PARSED: " + answerKey.size() + " answers ===");
        if (answerKey.isEmpty()) {
            System.out.println("WARNING: No answers were extracted from the answer key PDF!");
        }
        
        return answerKey;
    }
    
    /**
     * Parse CSV file containing exam questions and answers
     * Supports multiple formats:
     * 1. Full format: Question, ChoiceA, ChoiceB, ChoiceC, ChoiceD, Answer
     * 2. Compact format with embedded answer: Question, ChoiceA, ChoiceB, ChoiceC, ChoiceD
     *    (Answer extracted from question text if it contains "Answer: ...")
     * 3. Simple format: Question (with embedded "Answer: ...")
     */
    private List<String> processCsvExam(MultipartFile file, HttpSession session, 
                                        Map<Integer, String> externalAnswerKey) throws IOException {
        List<String> questionBlocks = new ArrayList<>();
        Map<Integer, String> answerKey = new HashMap<>();
        
        System.out.println("=== PROCESSING CSV EXAM ===");
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine(); // Read first line
            System.out.println("CSV First Line: " + headerLine);
            
            // Detect CSV format based on headers
            String[] headerCols = parseCsvLine(headerLine);
            boolean hasHeader = headerLine.toLowerCase().contains("question") || 
                               headerLine.toLowerCase().contains("choice") ||
                               headerLine.toLowerCase().contains("answer") ||
                               headerLine.toLowerCase().contains("difficulty");
            
            // Detect format type
            boolean isOpenEndedFormat = headerLine.toLowerCase().contains("question_text") || 
                                       (headerLine.toLowerCase().contains("question_number") && 
                                        headerLine.toLowerCase().contains("difficulty"));
            
            System.out.println("Format detected: " + (isOpenEndedFormat ? "Open-Ended" : "Multiple-Choice"));
            
            if (isOpenEndedFormat) {
                // Process open-ended format (Question_Number, Difficulty, Question_Text)
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] columns = parseCsvLine(line);
                    
                    if (columns.length >= 3) {
                        int qNum = Integer.parseInt(columns[0].trim());
                        String difficulty = columns[1].trim();
                        String questionText = columns[2].trim();
                        
                        // Mark as text-input question with special prefix
                        questionBlocks.add("[TEXT_INPUT]" + questionText);
                        System.out.println("Parsed Open-Ended Q" + qNum + " (" + difficulty + "): " + questionText);
                    }
                }
            } else {
                // Process multiple-choice format
                // Process first line if it's not a header
                if (!hasHeader && headerLine != null && !headerLine.trim().isEmpty()) {
                    processCSVRow(headerLine, 1, questionBlocks, answerKey);
                }
                
                String line;
                int questionNumber = hasHeader ? 1 : 2;
                
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    processCSVRow(line, questionNumber, questionBlocks, answerKey);
                    questionNumber++;
                }
            }
        }
        
        System.out.println("=== CSV EXAM PARSED: " + questionBlocks.size() + " questions ===");
        
        // Merge external answer key if provided
        if (externalAnswerKey != null && !externalAnswerKey.isEmpty()) {
            System.out.println("Merging external answer key with " + externalAnswerKey.size() + " answers");
            answerKey.putAll(externalAnswerKey);
        }
        
        // Shuffle questions with Fisher-Yates
        List<QuestionWithAnswer> questionsWithAnswers = new ArrayList<>();
        for (int i = 0; i < questionBlocks.size(); i++) {
            String question = questionBlocks.get(i);
            String answer = answerKey.get(i + 1);
            questionsWithAnswers.add(new QuestionWithAnswer(question, answer, i + 1));
        }
        
        SecureRandom rand = new SecureRandom();
        Collections.shuffle(questionsWithAnswers, rand);
        
        // Rebuild with shuffled order
        questionBlocks.clear();
        answerKey.clear();
        for (int i = 0; i < questionsWithAnswers.size(); i++) {
            QuestionWithAnswer qa = questionsWithAnswers.get(i);
            
            // Only shuffle choices for multiple-choice questions
            String shuffledQuestion;
            if (qa.question.startsWith("[TEXT_INPUT]")) {
                // Keep text-input questions as-is
                shuffledQuestion = qa.question;
            } else {
                // Re-shuffle choices within each multiple-choice question
                shuffledQuestion = reshuffleQuestionChoices(qa.question, rand);
            }
            
            questionBlocks.add(shuffledQuestion);
            answerKey.put(i + 1, qa.answer);
            
            System.out.println("Shuffled Q" + (i + 1) + " (originally Q" + qa.originalNumber + ") -> " + qa.answer);
        }
        
        session.setAttribute("correctAnswerKey", answerKey);
        return questionBlocks;
    }
    
    /**
     * Process a single CSV row - handles multiple formats
     */
    private void processCSVRow(String line, int questionNumber, 
                               List<String> questionBlocks, Map<Integer, String> answerKey) {
        String[] columns = parseCsvLine(line);
        
        if (columns.length >= 6) {
            // Format 1: Question, ChoiceA, ChoiceB, ChoiceC, ChoiceD, Answer
            String questionText = columns[0].trim();
            String choiceA = columns[1].trim();
            String choiceB = columns[2].trim();
            String choiceC = columns[3].trim();
            String choiceD = columns[4].trim();
            String correctAnswer = columns[5].trim();
            
            // Build question block
            StringBuilder questionBlock = new StringBuilder();
            questionBlock.append(questionText).append("\n");
            questionBlock.append("A) ").append(choiceA).append("\n");
            questionBlock.append("B) ").append(choiceB).append("\n");
            questionBlock.append("C) ").append(choiceC).append("\n");
            questionBlock.append("D) ").append(choiceD);
            
            questionBlocks.add(questionBlock.toString());
            answerKey.put(questionNumber, correctAnswer);
            
            System.out.println("Parsed CSV Q" + questionNumber + " (Format 1) -> " + correctAnswer);
            
        } else if (columns.length >= 5) {
            // Format 2: Question, ChoiceA, ChoiceB, ChoiceC, ChoiceD
            // Try to extract answer from question text
            String questionText = columns[0].trim();
            String choiceA = columns[1].trim();
            String choiceB = columns[2].trim();
            String choiceC = columns[3].trim();
            String choiceD = columns[4].trim();
            
            // Extract answer if embedded in question
            String correctAnswer = extractEmbeddedAnswer(questionText);
            if (correctAnswer != null) {
                // Remove the answer from question text
                questionText = questionText.replaceAll("(?i)\\s*answer\\s*:\\s*.*$", "").trim();
            }
            
            // Build question block
            StringBuilder questionBlock = new StringBuilder();
            questionBlock.append(questionText).append("\n");
            questionBlock.append("A) ").append(choiceA).append("\n");
            questionBlock.append("B) ").append(choiceB).append("\n");
            questionBlock.append("C) ").append(choiceC).append("\n");
            questionBlock.append("D) ").append(choiceD);
            
            questionBlocks.add(questionBlock.toString());
            if (correctAnswer != null) {
                answerKey.put(questionNumber, correctAnswer);
                System.out.println("Parsed CSV Q" + questionNumber + " (Format 2 with embedded answer) -> " + correctAnswer);
            } else {
                System.out.println("Parsed CSV Q" + questionNumber + " (Format 2 - no answer found)");
            }
            
        } else if (columns.length == 1) {
            // Format 3: Single column with question containing embedded answer and choices
            String fullText = columns[0].trim();
            
            // Try to extract answer
            String correctAnswer = extractEmbeddedAnswer(fullText);
            
            // Remove answer line from text
            fullText = fullText.replaceAll("(?i)\\s*answer\\s*:\\s*.*$", "").trim();
            
            // Check if it already has choices formatted
            if (fullText.contains("\n") && fullText.matches("(?s).*[A-D]\\).*")) {
                // Already has formatted choices
                questionBlocks.add(fullText);
            } else {
                // Plain question without formatted choices
                questionBlocks.add(fullText);
            }
            
            if (correctAnswer != null) {
                answerKey.put(questionNumber, correctAnswer);
                System.out.println("Parsed CSV Q" + questionNumber + " (Format 3 with embedded answer) -> " + correctAnswer);
            } else {
                System.out.println("Parsed CSV Q" + questionNumber + " (Format 3 - no answer found)");
            }
            
        } else {
            System.out.println("WARNING: Skipping malformed CSV line: " + line);
        }
    }
    
    /**
     * Extract embedded answer from question text (e.g., "Answer: Paris")
     */
    private String extractEmbeddedAnswer(String text) {
        if (text.toLowerCase().contains("answer:")) {
            String[] parts = text.split("(?i)answer\\s*:\\s*", 2);
            if (parts.length == 2) {
                return parts[1].trim();
            }
        } else if (text.toLowerCase().contains("correct:")) {
            String[] parts = text.split("(?i)correct\\s*:\\s*", 2);
            if (parts.length == 2) {
                return parts[1].trim();
            }
        }
        return null;
    }
    
    /**
     * Parse CSV answer key file
     * Expected format: QuestionNumber, Answer (or just Answer per line)
     */
    private Map<Integer, String> parseAnswerKeyCsv(MultipartFile file) throws IOException {
        Map<Integer, String> answerKey = new HashMap<>();
        
        System.out.println("=== PARSING CSV ANSWER KEY ===");
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String firstLine = reader.readLine();
            boolean hasHeader = firstLine != null && 
                               (firstLine.toLowerCase().contains("question") || 
                                firstLine.toLowerCase().contains("answer"));
            
            if (!hasHeader && firstLine != null) {
                // Process first line as data
                String[] columns = parseCsvLine(firstLine);
                if (columns.length >= 2) {
                    int qNum = Integer.parseInt(columns[0].trim());
                    answerKey.put(qNum, columns[1].trim());
                } else if (columns.length == 1) {
                    answerKey.put(1, columns[0].trim());
                }
            }
            
            String line;
            int questionNumber = hasHeader ? 1 : 2;
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] columns = parseCsvLine(line);
                
                if (columns.length >= 2) {
                    // Format: QuestionNumber, Answer
                    int qNum = Integer.parseInt(columns[0].trim());
                    answerKey.put(qNum, columns[1].trim());
                    System.out.println("Parsed CSV Answer Key Q" + qNum + " -> " + columns[1].trim());
                } else if (columns.length == 1) {
                    // Format: Just answers per line
                    answerKey.put(questionNumber, columns[0].trim());
                    System.out.println("Parsed CSV Answer Key Q" + questionNumber + " -> " + columns[0].trim());
                    questionNumber++;
                }
            }
        }
        
        System.out.println("=== CSV ANSWER KEY PARSED: " + answerKey.size() + " answers ===");
        return answerKey;
    }
    
    /**
     * Parse a CSV line handling quoted fields and commas within quotes
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        fields.add(currentField.toString());
        return fields.toArray(new String[0]);
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
        
        // Shuffle the question order to prevent cheating
        // Create a mapping to preserve answer key association
        List<QuestionWithAnswer> questionsWithAnswers = new ArrayList<>();
        for (int i = 0; i < questionBlocks.size(); i++) {
            String question = questionBlocks.get(i);
            String answer = answerKey.get(i + 1); // Answer key is 1-indexed
            
            if (answer == null) {
                System.out.println("WARNING: No answer found for question " + (i + 1));
                answer = "Not Set"; // Provide a default to make it visible
            }
            
            questionsWithAnswers.add(new QuestionWithAnswer(question, answer, i + 1));
        }
        
        // Shuffle questions with their answers
        Collections.shuffle(questionsWithAnswers, rand);
        
        // Rebuild questionBlocks and answerKey with new order
        questionBlocks.clear();
        answerKey.clear();
        for (int i = 0; i < questionsWithAnswers.size(); i++) {
            QuestionWithAnswer qa = questionsWithAnswers.get(i);
            questionBlocks.add(qa.question);
            answerKey.put(i + 1, qa.answer);
            System.out.println("Shuffled Q" + (i + 1) + " (originally Q" + qa.originalNumber + ") -> " + qa.answer);
        }
        
        // Update session with the shuffled answer key
        session.setAttribute("correctAnswerKey", answerKey);
        
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
        // Use FisherYatesService for proper implementation
        fisherYatesService.shuffle(choices, rand);
        
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

        // Add title with better formatting
        Paragraph title = new Paragraph("EXAMINATION PAPER");
        title.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph(" "));
        
        // Add student information section with proper spacing
        Paragraph nameField = new Paragraph("NAME: ________________________________________");
        document.add(nameField);
        document.add(new Paragraph(" "));
        
        Paragraph dateField = new Paragraph("DATE: ____________________");
        document.add(dateField);
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));

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

        // Add blank line
        document.createParagraph().createRun().addBreak();
        
        // Add NAME field
        XWPFParagraph namePara = document.createParagraph();
        XWPFRun nameRun = namePara.createRun();
        nameRun.setText("NAME: ________________________________________");
        nameRun.addBreak();
        
        // Add DATE field
        XWPFParagraph datePara = document.createParagraph();
        XWPFRun dateRun = datePara.createRun();
        dateRun.setText("DATE: ____________________");
        dateRun.addBreak();
        dateRun.addBreak();

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
    @GetMapping("/download-results")
    public ResponseEntity<byte[]> downloadResults() throws IOException {
        List<ExamSubmission> submissions = examSubmissionRepository.findAll();
        
        if (submissions.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        StringBuilder csv = new StringBuilder();
        // CSV Header
        csv.append("Student Email,Exam Name,Score,Total Questions,Percentage,Performance Category,")
           .append("Topic Mastery,Difficulty Resilience,Accuracy,Time Efficiency,Confidence,")
           .append("Submitted Date,Released Date\n");
        
        // Add each submission
        for (ExamSubmission submission : submissions) {
            csv.append(escapeCSV(submission.getStudentEmail())).append(",");
            csv.append(escapeCSV(submission.getExamName())).append(",");
            csv.append(submission.getScore()).append(",");
            csv.append(submission.getTotalQuestions()).append(",");
            csv.append(String.format("%.2f", submission.getPercentage())).append(",");
            csv.append(escapeCSV(submission.getPerformanceCategory())).append(",");
            csv.append(String.format("%.2f", submission.getTopicMastery())).append(",");
            csv.append(String.format("%.2f", submission.getDifficultyResilience())).append(",");
            csv.append(String.format("%.2f", submission.getAccuracy())).append(",");
            csv.append(String.format("%.2f", submission.getTimeEfficiency())).append(",");
            csv.append(String.format("%.2f", submission.getConfidence())).append(",");
            csv.append(submission.getSubmittedAt().toString()).append(",");
            csv.append(submission.getReleasedAt() != null ? submission.getReleasedAt().toString() : "N/A");
            csv.append("\n");
        }
        
        byte[] csvBytes = csv.toString().getBytes("UTF-8");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "exam_results_" + System.currentTimeMillis() + ".csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }
    
    @GetMapping("/download-result/{submissionId}")
    public ResponseEntity<byte[]> downloadIndividualResult(@PathVariable Long submissionId) throws IOException {
        Optional<ExamSubmission> submissionOpt = examSubmissionRepository.findById(submissionId);
        
        if (submissionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ExamSubmission submission = submissionOpt.get();
        
        StringBuilder csv = new StringBuilder();
        // CSV Header
        csv.append("Student Email,Exam Name,Score,Total Questions,Percentage,Performance Category,");
        csv.append("Topic Mastery,Difficulty Resilience,Accuracy,Time Efficiency,Confidence,");
        csv.append("Submitted Date,Released Date\n");
        
        // Add submission data
        csv.append(escapeCSV(submission.getStudentEmail())).append(",");
        csv.append(escapeCSV(submission.getExamName())).append(",");
        csv.append(submission.getScore()).append(",");
        csv.append(submission.getTotalQuestions()).append(",");
        csv.append(String.format("%.2f", submission.getPercentage())).append(",");
        csv.append(escapeCSV(submission.getPerformanceCategory())).append(",");
        csv.append(String.format("%.2f", submission.getTopicMastery())).append(",");
        csv.append(String.format("%.2f", submission.getDifficultyResilience())).append(",");
        csv.append(String.format("%.2f", submission.getAccuracy())).append(",");
        csv.append(String.format("%.2f", submission.getTimeEfficiency())).append(",");
        csv.append(String.format("%.2f", submission.getConfidence())).append(",");
        csv.append(submission.getSubmittedAt().toString()).append(",");
        csv.append(submission.getReleasedAt() != null ? submission.getReleasedAt().toString() : "N/A");
        csv.append("\n");
        
        // Add detailed answers if available
        if (submission.getAnswerDetailsJson() != null && !submission.getAnswerDetailsJson().isEmpty()) {
            csv.append("\n\nDetailed Answers:\n");
            csv.append("Question Number,Student Answer,Correct Answer,Result\n");
            
            String[] details = submission.getAnswerDetailsJson().split(";");
            for (String detail : details) {
                if (!detail.trim().isEmpty()) {
                    String[] parts = detail.split("\\|");
                    if (parts.length == 4) {
                        csv.append(parts[0]).append(","); // Question number
                        csv.append(escapeCSV(parts[1])).append(","); // Student answer
                        csv.append(escapeCSV(parts[2])).append(","); // Correct answer
                        csv.append(parts[3].equals("true") ? "Correct" : "Incorrect").append("\n");
                    }
                }
            }
        }
        
        byte[] csvBytes = csv.toString().getBytes("UTF-8");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        String filename = "result_" + submission.getStudentEmail().replace("@", "_") + "_" + submissionId + ".csv";
        headers.setContentDispositionFormData("attachment", filename);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }
    
    /**
     * Escape special characters in CSV
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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