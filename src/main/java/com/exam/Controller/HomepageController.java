package com.exam.Controller;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.exam.entity.EnrolledStudent;
import com.exam.entity.ExamSubmission;
import com.exam.entity.Subject;
import com.exam.entity.User;
import com.exam.repository.EnrolledStudentRepository;
import com.exam.repository.ExamSubmissionRepository;
import com.exam.repository.SubjectRepository;
import com.exam.repository.UserRepository;
import com.exam.service.AnswerKeyService;
import com.exam.service.FisherYatesService;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;

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
    
    @Autowired
    private SubjectRepository subjectRepository;

    private static Map<String, List<String>> distributedExams = new HashMap<>();
    
    // Store uploaded exams with their metadata
    private static Map<String, UploadedExam> uploadedExams = new HashMap<>();

    /**
     * Regex: detect LaTeX segments (\command{} or var^{} or var_{}) for auto-wrapping in $...$.
     * Segment = one or more LaTeX tokens optionally connected by math operators / whitespace.
     */
    private static final Pattern LATEX_SEGMENT = Pattern.compile(
        "(?:\\\\[a-zA-Z]+(?:\\{[^}]*\\})*|[a-zA-Z0-9]*(?:\\^\\{[^}]+\\}|_\\{[^}]+\\}))" +
        "(?:[0-9a-zA-Z+\\-*/=<>().^_{} ]*" +
            "(?:\\\\[a-zA-Z]+(?:\\{[^}]*\\})*|[a-zA-Z0-9]*(?:\\^\\{[^}]+\\}|_\\{[^}]+\\}))" +
        ")*"
    );
    
    // Helper class to store exam metadata
    public static class UploadedExam {
        private String examId;
        private String examName;
        private String subject;
        private String activityType;
        private List<String> questions;
        private List<String> difficulties; // Store difficulty for each question
        private Map<Integer, String> answerKey;
        private java.time.LocalDateTime uploadedAt;
        
        public UploadedExam(String examId, String examName, String subject, String activityType, 
                          List<String> questions, List<String> difficulties, Map<Integer, String> answerKey) {
            this.examId = examId;
            this.examName = examName;
            this.subject = subject;
            this.activityType = activityType;
            this.questions = questions;
            this.difficulties = difficulties;
            this.answerKey = answerKey;
            this.uploadedAt = java.time.LocalDateTime.now();
        }
        
        public String getExamId() { return examId; }
        public String getExamName() { return examName; }
        public String getSubject() { return subject; }
        public String getActivityType() { return activityType; }
        public List<String> getQuestions() { return questions; }
        public List<String> getDifficulties() { return difficulties; }
        public Map<Integer, String> getAnswerKey() { return answerKey; }
        public java.time.LocalDateTime getUploadedAt() { return uploadedAt; }
    }
    
    // Helper class for shuffling questions while preserving answer associations
    private static class QuestionWithAnswer {
        String question;
        String answer;
        String difficulty;
        int originalNumber;
        
        QuestionWithAnswer(String question, String answer, String difficulty, int originalNumber) {
            this.question = question;
            this.answer = answer;
            this.difficulty = difficulty;
            this.originalNumber = originalNumber;
        }
    }
    
    // Helper class to return both questions and difficulties from CSV processing
    private static class CsvProcessResult {
        List<String> questions;
        List<String> difficulties;
        
        CsvProcessResult(List<String> questions, List<String> difficulties) {
            this.questions = questions;
            this.difficulties = difficulties;
        }
    }

    // Store for unlocked exams (studentEmail -> examName)
    private static Map<String, Set<String>> unlockedExams = new HashMap<>();

    /**
     * Initialize null boolean fields in existing ExamSubmission records
     */
    @PostConstruct
    public void initializeNullBooleans() {
        try {
            List<ExamSubmission> submissions = examSubmissionRepository.findAll();
            boolean needsUpdate = false;
            
            for (ExamSubmission submission : submissions) {
                // Check if isGraded is null (it's a Boolean object in the entity)
                if (submission.isGraded() == null) {
                    submission.setGraded(false);
                    needsUpdate = true;
                }
            }
            
            if (needsUpdate) {
                examSubmissionRepository.saveAll(submissions);
                System.out.println("✅ Initialized null boolean fields in " + submissions.size() + " ExamSubmission records");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Could not initialize null boolean fields: " + e.getMessage());
        }
    }

    // Public getter for accessing distributed exams from other controllers
    public static Map<String, List<String>> getDistributedExams() {
        return distributedExams;
    }
    
    // Check if exam is unlocked for student
    public static boolean isExamUnlocked(String studentEmail, String examName) {
        Set<String> studentUnlocks = unlockedExams.get(studentEmail);
        return studentUnlocks != null && studentUnlocks.contains(examName);
    }
    
    // Remove unlock after submission
    public static void removeUnlock(String studentEmail, String examName) {
        Set<String> studentUnlocks = unlockedExams.get(studentEmail);
        if (studentUnlocks != null) {
            studentUnlocks.remove(examName);
        }
    }

    // Remove student from distributed exams after they have submitted
    public static void removeDistributedExam(String studentEmail) {
        distributedExams.remove(studentEmail);
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
        
        return showHomepageCommon(model, teacherEmail, allStudents, enrolledStudents);
    }
    
    /**
     * Common method for homepage rendering
     */
    private String showHomepageCommon(Model model, String teacherEmail, List<User> allStudents, List<EnrolledStudent> enrolledStudents) {
        
        // Fetch exam submissions and sort by latest first
        List<ExamSubmission> submissions = examSubmissionRepository.findAll();
        submissions.sort(Comparator.comparing(ExamSubmission::getSubmittedAt, 
                                             Comparator.nullsLast(Comparator.reverseOrder())));
        
        // Create a map to track distributed exams and their status
        Map<String, Map<String, Object>> distributedExamStatus = new HashMap<>();
        for (EnrolledStudent student : enrolledStudents) {
            String studentEmail = student.getStudentEmail();
            if (distributedExams.containsKey(studentEmail)) {
                Map<String, Object> status = new HashMap<>();
                status.put("studentName", student.getStudentName());
                status.put("studentEmail", studentEmail);
                status.put("hasExam", true);
                
                // Check if student has submitted
                String examNameForStudent = (String) null; // We'll need to track this
                boolean hasSubmitted = submissions.stream()
                    .anyMatch(sub -> sub.getStudentEmail().equals(studentEmail));
                status.put("hasSubmitted", hasSubmitted);
                status.put("isUnlocked", unlockedExams.containsKey(studentEmail) && !unlockedExams.get(studentEmail).isEmpty());
                
                distributedExamStatus.put(studentEmail, status);
            }
        }
        
        model.addAttribute("teacherEmail", teacherEmail);
        model.addAttribute("enrolledStudents", enrolledStudents);
        model.addAttribute("allStudents", allStudents);
        model.addAttribute("submissions", submissions);
        model.addAttribute("uploadedExams", new ArrayList<>(uploadedExams.values()));
        model.addAttribute("distributedExamStatus", distributedExamStatus);
        
        // Get subjects for this teacher
        List<Subject> subjects = subjectRepository.findByTeacherEmail(teacherEmail);
        model.addAttribute("subjects", subjects);
        
        // Group enrolled students by subject ID for easy template access
        Map<Long, List<EnrolledStudent>> enrollmentsBySubject = new HashMap<>();
        Map<Long, Integer> enrollmentCountBySubject = new HashMap<>();
        for (EnrolledStudent enrollment : enrolledStudents) {
            Long subjectId = enrollment.getSubjectId();
            if (subjectId != null) {
                enrollmentsBySubject.computeIfAbsent(subjectId, k -> new ArrayList<>()).add(enrollment);
                enrollmentCountBySubject.put(subjectId, enrollmentsBySubject.get(subjectId).size());
            }
        }
        model.addAttribute("enrollmentsBySubject", enrollmentsBySubject);
        model.addAttribute("enrollmentCountBySubject", enrollmentCountBySubject);
        
        return "homepage";
    }
    
    /**
     * Show subjects management page
     */
    @GetMapping("/subjects")
    public String showSubjects(Model model, java.security.Principal principal) {
        String teacherEmail = principal.getName();
        
        // Fetch all students from database
        List<User> allStudents = userRepository.findAll().stream()
            .filter(user -> user.getRole() == User.Role.STUDENT)
            .collect(Collectors.toList());
        
        // Fetch enrolled students for this teacher
        List<EnrolledStudent> enrolledStudents = enrolledStudentRepository.findByTeacherEmail(teacherEmail);
        
        // Get subjects for this teacher
        List<Subject> subjects = subjectRepository.findByTeacherEmail(teacherEmail);
        model.addAttribute("subjects", subjects);
        
        // Group enrolled students by subject ID for easy template access
        Map<Long, List<EnrolledStudent>> enrollmentsBySubject = new HashMap<>();
        Map<Long, Integer> enrollmentCountBySubject = new HashMap<>();
        for (EnrolledStudent enrollment : enrolledStudents) {
            Long subjectId = enrollment.getSubjectId();
            if (subjectId != null) {
                enrollmentsBySubject.computeIfAbsent(subjectId, k -> new ArrayList<>()).add(enrollment);
                enrollmentCountBySubject.put(subjectId, enrollmentsBySubject.get(subjectId).size());
            }
        }
        
        model.addAttribute("enrollmentsBySubject", enrollmentsBySubject);
        model.addAttribute("enrollmentCountBySubject", enrollmentCountBySubject);
        model.addAttribute("allStudents", allStudents);
        model.addAttribute("teacherEmail", teacherEmail);
        
        return "teacher-subjects";
    }
    
    /**
     * Show submissions management page
     */
    @GetMapping("/submissions")
    public String showSubmissions(Model model, 
                                   @RequestParam(required = false) String examFilter,
                                   @RequestParam(required = false) String subjectFilter,
                                   @RequestParam(required = false) String statusFilter,
                                   @RequestParam(required = false) String studentFilter,
                                   java.security.Principal principal) {
        String teacherEmail = principal.getName();
        
        // Get all enrolled students for this teacher
        List<EnrolledStudent> enrolledStudents = enrolledStudentRepository.findByTeacherEmail(teacherEmail);
        List<String> studentEmails = enrolledStudents.stream()
                .map(EnrolledStudent::getStudentEmail)
                .collect(Collectors.toList());
        
        // Get submissions only for this teacher's enrolled students
        List<ExamSubmission> allSubmissions;
        if (studentEmails.isEmpty()) {
            allSubmissions = new ArrayList<>();
        } else {
            allSubmissions = examSubmissionRepository.findByStudentEmailIn(studentEmails).stream()
                    .sorted(Comparator.comparing(ExamSubmission::getSubmittedAt,
                                                Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());
        }
        
        // Apply filters
        List<ExamSubmission> filteredSubmissions = allSubmissions;
        
        if (examFilter != null && !examFilter.isEmpty()) {
            filteredSubmissions = filteredSubmissions.stream()
                    .filter(s -> s.getExamName().equals(examFilter))
                    .collect(Collectors.toList());
        }
        
        if (subjectFilter != null && !subjectFilter.isEmpty()) {
            filteredSubmissions = filteredSubmissions.stream()
                    .filter(s -> s.getSubject().equals(subjectFilter))
                    .collect(Collectors.toList());
        }
        
        if (studentFilter != null && !studentFilter.isEmpty()) {
            filteredSubmissions = filteredSubmissions.stream()
                    .filter(s -> s.getStudentEmail().equals(studentFilter))
                    .collect(Collectors.toList());
        }
        
        if (statusFilter != null && !statusFilter.isEmpty()) {
            switch (statusFilter) {
                case "graded":
                    filteredSubmissions = filteredSubmissions.stream()
                            .filter(ExamSubmission::isGraded)
                            .collect(Collectors.toList());
                    break;
                case "pending":
                    filteredSubmissions = filteredSubmissions.stream()
                            .filter(s -> !s.isGraded())
                            .collect(Collectors.toList());
                    break;
                case "released":
                    filteredSubmissions = filteredSubmissions.stream()
                            .filter(ExamSubmission::isResultsReleased)
                            .collect(Collectors.toList());
                    break;
            }
        }
        
        // Calculate statistics
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSubmissions", filteredSubmissions.size());
        stats.put("gradedCount", filteredSubmissions.stream().filter(ExamSubmission::isGraded).count());
        stats.put("pendingCount", filteredSubmissions.stream().filter(s -> !s.isGraded()).count());
        stats.put("releasedCount", filteredSubmissions.stream().filter(ExamSubmission::isResultsReleased).count());
        
        // Get unique exams, subjects, and students for filter dropdowns
        List<String> uniqueExams = allSubmissions.stream()
                .map(ExamSubmission::getExamName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        List<String> uniqueSubjects = allSubmissions.stream()
                .map(ExamSubmission::getSubject)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        List<String> uniqueStudents = allSubmissions.stream()
                .map(ExamSubmission::getStudentEmail)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        model.addAttribute("submissions", filteredSubmissions);
        model.addAttribute("stats", stats);
        model.addAttribute("uniqueExams", uniqueExams);
        model.addAttribute("uniqueSubjects", uniqueSubjects);
        model.addAttribute("uniqueStudents", uniqueStudents);
        model.addAttribute("examFilter", examFilter);
        model.addAttribute("subjectFilter", subjectFilter);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("studentFilter", studentFilter);
        
        return "teacher-submissions";
    }

    /**
     * Show the manage questions page for a specific exam
     */
    @GetMapping("/manage-questions/{examId}")
    public String showManageQuestions(@PathVariable String examId, Model model) {
        UploadedExam exam = uploadedExams.get(examId);
        
        if (exam == null) {
            return "redirect:/teacher/homepage";
        }
        
        model.addAttribute("exam", exam);
        return "manage-questions";
    }
    
    /**
     * Add a new question to an existing exam
     */
    @PostMapping("/add-question")
    public String addQuestion(@RequestParam String examId,
                             @RequestParam String questionText,
                             @RequestParam String answer,
                             @RequestParam String difficulty,
                             Model model,
                             org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        UploadedExam exam = uploadedExams.get(examId);
        
        if (exam == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Exam not found!");
            return "redirect:/teacher/homepage";
        }
        
        try {   
            // Add the new question to the existing lists
            exam.getQuestions().add(questionText);
            exam.getDifficulties().add(difficulty);
            
            // Add answer to the answer key (using the next question number)
            int questionNumber = exam.getQuestions().size() - 1; // 0-based index
            exam.getAnswerKey().put(questionNumber, answer);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Question added successfully! Total questions: " + exam.getQuestions().size());
            
            System.out.println("✅ Added question to exam: " + exam.getExamName() + 
                             " | Total: " + exam.getQuestions().size() + " questions");
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error adding question: " + e.getMessage());
            e.printStackTrace();
        }
        
        return "redirect:/teacher/manage-questions/" + examId;
    }
    
    /**
     * Delete a question from an existing exam
     */
    @PostMapping("/delete-question")
    public String deleteQuestion(@RequestParam String examId,
                                @RequestParam int questionIndex,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        UploadedExam exam = uploadedExams.get(examId);
        
        if (exam == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Exam not found!");
            return "redirect:/teacher/homepage";
        }
        
        try {
            if (questionIndex >= 0 && questionIndex < exam.getQuestions().size()) {
                // Remove question and difficulty at the specified index
                exam.getQuestions().remove(questionIndex);
                exam.getDifficulties().remove(questionIndex);
                
                // Rebuild answer key with new indices
                Map<Integer, String> newAnswerKey = new HashMap<>();
                for (Map.Entry<Integer, String> entry : exam.getAnswerKey().entrySet()) {
                    int oldIndex = entry.getKey();
                    if (oldIndex < questionIndex) {
                        // Keep indices before deleted question
                        newAnswerKey.put(oldIndex, entry.getValue());
                    } else if (oldIndex > questionIndex) {
                        // Shift down indices after deleted question
                        newAnswerKey.put(oldIndex - 1, entry.getValue());
                    }
                    // Skip the deleted question index
                }
                exam.getAnswerKey().clear();
                exam.getAnswerKey().putAll(newAnswerKey);
                
                redirectAttributes.addFlashAttribute("successMessage", 
                    "Question deleted successfully! Total questions: " + exam.getQuestions().size());
                
                System.out.println("✅ Deleted question from exam: " + exam.getExamName() + 
                                 " | Remaining: " + exam.getQuestions().size() + " questions");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Invalid question index!");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error deleting question: " + e.getMessage());
            e.printStackTrace();
        }
        
        return "redirect:/teacher/manage-questions/" + examId;
    }

    @PostMapping("/enroll-student")
    public String enrollStudent(@RequestParam String studentEmail,
                               @RequestParam Long subjectId,
                               java.security.Principal principal) {
        String teacherEmail = principal.getName();
        
        // Get subject details
        Optional<Subject> subjectOpt = subjectRepository.findById(subjectId);
        if (subjectOpt.isEmpty() || !subjectOpt.get().getTeacherEmail().equals(teacherEmail)) {
            return "redirect:/teacher/homepage";
        }
        
        Subject subject = subjectOpt.get();
        
        // Check if already enrolled in this subject
        Optional<EnrolledStudent> existing = enrolledStudentRepository
            .findByTeacherEmailAndStudentEmailAndSubjectId(teacherEmail, studentEmail, subjectId);
        
        if (existing.isEmpty()) {
            // Get student name
            Optional<User> studentOpt = userRepository.findByEmail(studentEmail);
            if (studentOpt.isPresent()) {
                EnrolledStudent enrollment = new EnrolledStudent(
                    teacherEmail,
                    studentEmail,
                    studentOpt.get().getFullName(),
                    subjectId,
                    subject.getSubjectName()
                );
                enrolledStudentRepository.save(enrollment);
                System.out.println("✅ Enrolled " + studentEmail + " in " + subject.getSubjectName());
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
    
    @PostMapping("/create-subject")
    public String createSubject(@RequestParam String subjectName,
                               @RequestParam(required = false) String description,
                               java.security.Principal principal) {
        String teacherEmail = principal.getName();
        
        // Check if subject already exists for this teacher
        if (!subjectRepository.existsBySubjectNameAndTeacherEmail(subjectName, teacherEmail)) {
            Subject subject = new Subject(subjectName, description, teacherEmail);
            subjectRepository.save(subject);
            System.out.println("✅ Subject created: " + subjectName + " by " + teacherEmail);
        }
        
        return "redirect:/teacher/homepage";
    }
    
    @PostMapping("/delete-subject")
    public String deleteSubject(@RequestParam Long subjectId, java.security.Principal principal) {
        String teacherEmail = principal.getName();
        
        // Verify the subject belongs to this teacher before deleting
        Optional<Subject> subjectOpt = subjectRepository.findById(subjectId);
        if (subjectOpt.isPresent() && subjectOpt.get().getTeacherEmail().equals(teacherEmail)) {
            subjectRepository.deleteById(subjectId);
            System.out.println("🗑️ Subject deleted: " + subjectOpt.get().getSubjectName());
        }
        
        return "redirect:/teacher/homepage";
    }
    
    @GetMapping("/subject-classroom/{subjectId}")
    public String viewSubjectClassroom(@PathVariable Long subjectId, Model model, java.security.Principal principal) {
        String teacherEmail = principal.getName();
        
        // Verify the subject belongs to this teacher
        Optional<Subject> subjectOpt = subjectRepository.findById(subjectId);
        if (subjectOpt.isEmpty() || !subjectOpt.get().getTeacherEmail().equals(teacherEmail)) {
            return "redirect:/teacher/homepage";
        }
        
        Subject subject = subjectOpt.get();
        
        // Get enrolled students for this subject
        List<EnrolledStudent> enrolledStudents = enrolledStudentRepository.findBySubjectId(subjectId);
        
        // Get submissions for students in this subject
        List<String> studentEmails = enrolledStudents.stream()
            .map(EnrolledStudent::getStudentEmail)
            .collect(Collectors.toList());
        
        List<ExamSubmission> submissions = examSubmissionRepository.findAll().stream()
            .filter(sub -> studentEmails.contains(sub.getStudentEmail()) && 
                          subject.getSubjectName().equals(sub.getSubject()))
            .sorted(Comparator.comparing(ExamSubmission::getSubmittedAt, 
                                       Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
        
        // Get all students for enrollment
        List<User> allStudents = userRepository.findAll().stream()
            .filter(user -> user.getRole() == User.Role.STUDENT)
            .collect(Collectors.toList());
        
        // Only show exams uploaded for this specific subject
        String subjectName = subject.getSubjectName();
        List<UploadedExam> subjectExams = uploadedExams.values().stream()
            .filter(exam -> subjectName.equals(exam.getSubject()))
            .collect(Collectors.toList());

        model.addAttribute("subject", subject);
        model.addAttribute("enrolledStudents", enrolledStudents);
        model.addAttribute("submissions", submissions);
        model.addAttribute("teacherEmail", teacherEmail);
        model.addAttribute("allStudents", allStudents);
        model.addAttribute("uploadedExams", subjectExams);
        
        return "subject-classroom";
    }

    @PostMapping("/distribute")
    public String distributeExam(@RequestParam String targetStudent,
                                 @RequestParam String examId,
                                 @RequestParam Integer timeLimit,
                                 @RequestParam String deadline,
                                 @RequestParam(defaultValue = "30") Integer easyPercent,
                                 @RequestParam(defaultValue = "50") Integer mediumPercent,
                                 @RequestParam(defaultValue = "20") Integer hardPercent,
                                 @RequestParam(required = false) Integer questionCount,
                                 HttpSession session) {
        doDistributeForStudent(targetStudent, examId, timeLimit, deadline, easyPercent, mediumPercent, hardPercent, questionCount, session);
        return "redirect:/teacher/homepage";
    }

    @PostMapping("/distribute-all")
    public String distributeToAll(@RequestParam Long subjectId,
                                  @RequestParam String examId,
                                  @RequestParam Integer timeLimit,
                                  @RequestParam String deadline,
                                  @RequestParam(defaultValue = "30") Integer easyPercent,
                                  @RequestParam(defaultValue = "50") Integer mediumPercent,
                                  @RequestParam(defaultValue = "20") Integer hardPercent,
                                  @RequestParam(required = false) Integer questionCount,
                                  HttpSession session) {
        List<EnrolledStudent> enrolledStudents = enrolledStudentRepository.findBySubjectId(subjectId);
        for (EnrolledStudent student : enrolledStudents) {
            doDistributeForStudent(student.getStudentEmail(), examId, timeLimit, deadline,
                                   easyPercent, mediumPercent, hardPercent, questionCount, session);
        }
        System.out.println("Distributed to all " + enrolledStudents.size() + " students in subject " + subjectId);
        return "redirect:/teacher/subject-classroom/" + subjectId;
    }

    private void doDistributeForStudent(String targetStudent, String examId, Integer timeLimit, String deadline,
                                        Integer easyPercent, Integer mediumPercent, Integer hardPercent,
                                        Integer questionCount, HttpSession session) {
        UploadedExam selectedExam = uploadedExams.get(examId);

        if (selectedExam != null) {
            // Create a fresh copy of questions and difficulties for this student
            List<String> allQuestions = new ArrayList<>(selectedExam.getQuestions());
            List<String> allDifficulties = new ArrayList<>(selectedExam.getDifficulties());
            Map<Integer, String> originalAnswerKey = selectedExam.getAnswerKey();

            // Categorize questions by difficulty using stored difficulty levels
            List<Integer> easyIndices = new ArrayList<>();
            List<Integer> mediumIndices = new ArrayList<>();
            List<Integer> hardIndices = new ArrayList<>();

            for (int i = 0; i < allQuestions.size(); i++) {
                String difficulty = allDifficulties.get(i);
                if (difficulty.equalsIgnoreCase("Easy")) {
                    easyIndices.add(i);
                } else if (difficulty.equalsIgnoreCase("Hard")) {
                    hardIndices.add(i);
                } else {
                    mediumIndices.add(i);
                }
            }

            // Calculate number of questions for each difficulty
            int totalQuestions = (questionCount != null && questionCount > 0 && questionCount <= allQuestions.size())
                ? questionCount
                : allQuestions.size();

            System.out.println("Distributing " + totalQuestions + " questions out of " + allQuestions.size() + " available");

            int easyCount = (int) Math.round(totalQuestions * easyPercent / 100.0);
            int mediumCount = (int) Math.round(totalQuestions * mediumPercent / 100.0);
            int hardCount = (int) Math.round(totalQuestions * hardPercent / 100.0);

            int calculatedTotal = easyCount + mediumCount + hardCount;
            if (calculatedTotal < totalQuestions) {
                mediumCount += (totalQuestions - calculatedTotal);
            } else if (calculatedTotal > totalQuestions) {
                mediumCount -= (calculatedTotal - totalQuestions);
            }

            SecureRandom rand = new SecureRandom();
            Collections.shuffle(easyIndices, rand);
            Collections.shuffle(mediumIndices, rand);
            Collections.shuffle(hardIndices, rand);

            List<String> selectedQuestions = new ArrayList<>();
            List<String> selectedDifficulties = new ArrayList<>();
            List<Integer> selectedOriginalIndices = new ArrayList<>();

            for (int i = 0; i < Math.min(easyCount, easyIndices.size()); i++) {
                int idx = easyIndices.get(i);
                selectedQuestions.add(allQuestions.get(idx));
                selectedDifficulties.add(allDifficulties.get(idx));
                selectedOriginalIndices.add(idx + 1);
            }
            for (int i = 0; i < Math.min(mediumCount, mediumIndices.size()); i++) {
                int idx = mediumIndices.get(i);
                selectedQuestions.add(allQuestions.get(idx));
                selectedDifficulties.add(allDifficulties.get(idx));
                selectedOriginalIndices.add(idx + 1);
            }
            for (int i = 0; i < Math.min(hardCount, hardIndices.size()); i++) {
                int idx = hardIndices.get(i);
                selectedQuestions.add(allQuestions.get(idx));
                selectedDifficulties.add(allDifficulties.get(idx));
                selectedOriginalIndices.add(idx + 1);
            }

            List<Integer> shuffleIndices = new ArrayList<>();
            for (int i = 0; i < selectedQuestions.size(); i++) shuffleIndices.add(i);
            Collections.shuffle(shuffleIndices, rand);

            List<String> finalQuestions = new ArrayList<>();
            List<String> finalDifficulties = new ArrayList<>();
            Map<Integer, String> studentAnswerKey = new HashMap<>();

            for (int newPos = 0; newPos < shuffleIndices.size(); newPos++) {
                int oldPos = shuffleIndices.get(newPos);
                finalQuestions.add(selectedQuestions.get(oldPos));
                finalDifficulties.add(selectedDifficulties.get(oldPos));

                int originalQuestionIndex = selectedOriginalIndices.get(oldPos);
                String answer = originalAnswerKey.get(originalQuestionIndex);
                if (answer != null) {
                    studentAnswerKey.put(newPos + 1, answer);
                    System.out.println("Q" + (newPos + 1) + " (originally Q" + originalQuestionIndex + ") -> Answer: " + answer);
                }
            }

            List<String> uniqueExam = new ArrayList<>();
            for (String questionBlock : finalQuestions) {
                uniqueExam.add(reshuffleQuestionChoices(questionBlock, rand));
            }

            distributedExams.put(targetStudent, uniqueExam);
            session.setAttribute("questionDifficulties_" + targetStudent, finalDifficulties);

            List<String> questionTopics = extractTopicsFromQuestions(finalQuestions, selectedExam.getSubject());
            session.setAttribute("questionTopics_" + targetStudent, questionTopics);
            System.out.println("📚 Extracted " + questionTopics.size() + " question topics for Random Forest");

            session.setAttribute("examSubject_" + targetStudent, selectedExam.getSubject());
            session.setAttribute("examActivityType_" + targetStudent, selectedExam.getActivityType());
            session.setAttribute("examName_" + targetStudent, selectedExam.getExamName());
            session.setAttribute("examTimeLimit_" + targetStudent, timeLimit);
            session.setAttribute("examDeadline_" + targetStudent, deadline);

            if (studentAnswerKey != null && !studentAnswerKey.isEmpty()) {
                answerKeyService.storeStudentAnswerKey(targetStudent, studentAnswerKey);
                System.out.println("Stored answer key for " + targetStudent + " with " + studentAnswerKey.size() + " answers");
            }

            long actualEasy   = finalDifficulties.stream().filter(d -> d.equalsIgnoreCase("Easy")).count();
            long actualMedium = finalDifficulties.stream().filter(d -> d.equalsIgnoreCase("Medium")).count();
            long actualHard   = finalDifficulties.stream().filter(d -> d.equalsIgnoreCase("Hard")).count();

            System.out.println("Distributed exam to: " + targetStudent);
            System.out.println("Distribution: " + actualEasy + " Easy, " + actualMedium + " Medium, " + actualHard + " Hard");
            System.out.println("Time limit: " + timeLimit + " minutes, Deadline: " + deadline);
        }
    }
    
    @PostMapping("/unlock-exam")
    public String unlockExam(@RequestParam String studentEmail,
                            HttpSession session) {
        String examName = (String) session.getAttribute("examName_" + studentEmail);
        
        if (examName != null) {
            // Add to unlocked exams
            unlockedExams.computeIfAbsent(studentEmail, k -> new HashSet<>()).add(examName);
            System.out.println("🔓 EXAM UNLOCKED: " + examName + " for student " + studentEmail);
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

    @GetMapping("/process-exams")
    public String processExamsGet() {
        // Redirect to homepage if someone tries to access this endpoint via GET
        return "redirect:/teacher/homepage";
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
            List<String> difficultyLevels = new ArrayList<>();
            
            if (isCsvFormat) {
                CsvProcessResult csvResult = processCsvExam(examCreated, session, answerKey);
                randomizedLines = csvResult.questions;
                difficultyLevels = csvResult.difficulties;
            } else {
                randomizedLines = processFisherYates(examCreated, session, answerKey);
                // For PDF files (exam "paper"), automatically infer difficulty per question
                for (String block : randomizedLines) {
                    String typeHint = block.matches("(?s).*[A-Da-d]\\)\\s+.*") ? "MULTIPLE_CHOICE" : "TEXT_INPUT";
                    String inferred = inferDifficultyFromQuestion(block, typeHint);
                    difficultyLevels.add(inferred);
                }
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
                                                         randomizedLines, difficultyLevels, finalAnswerKey);
            uploadedExams.put(examId, uploadedExam);
            
            // Build sorted answer key list so teacher can see it on the results page
            if (finalAnswerKey != null && !finalAnswerKey.isEmpty()) {
                List<Map<String, Object>> answerKeyDisplay = new ArrayList<>();
                for (int i = 1; i <= finalAnswerKey.size(); i++) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("number", i);
                    entry.put("answer", finalAnswerKey.getOrDefault(i, "Not Set"));
                    answerKeyDisplay.add(entry);
                }
                model.addAttribute("answerKeyDisplay", answerKeyDisplay);
            }
            model.addAttribute("type", "exam");
            model.addAttribute("examUploaded", true);
        }
        return "results";
    }
    
    /**
     * Parse a separate answer key PDF.
     *
     * Handles three common formats automatically:
     *
     * Format A – combined Q+A document (most common when teacher exports from an AI/generator):
     *   Q1 (Easy): Solve the equation for x: 3x^2 - 5x + 2 = 0
     *   Answer: x = 1 or x = 2/3
     *
     * Format B – standalone answer list with Q# prefix:
     *   Q1: x = 1 or x = 2/3
     *   Q1 (Easy): x = 1 or x = 2/3
     *
     * Format C – numbered list:
     *   1. x = 1 or x = 2/3
     *   1) x = 1 or x = 2/3
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

        // Skip document metadata / headers
        Pattern skipPattern = Pattern.compile(
            "(?i)(page\\s*\\d+|examination\\s+paper|name:|date:|answer\\s+key|confidential|" +
            "date\\s+generated|instructions?|total\\s+marks|student\\s+name:|id\\s+number:)",
            Pattern.CASE_INSENSITIVE
        );

        // Q# header: "Q1:", "Q1 (Easy):", "Question 1 (Medium):", etc.
        Pattern questionHeaderPattern = Pattern.compile(
            "(?i)^(?:question|q)\\s*(\\d+)\\s*(?:\\([^)]*\\))?\\s*:(.*)");

        // Numbered list: "1. answer" or "1) answer"
        Pattern numberedPattern = Pattern.compile("^(\\d+)[.)]\\s+(.+)");

        // Explicit answer line: "Answer: ...", "Correct: ...", "Correct Answer: ..."
        Pattern answerLinePattern = Pattern.compile(
            "(?i)^(?:correct\\s+)?answer\\s*:\\s*(.+)");

        // ── First pass: decide which format the PDF uses ──────────────────────────
        boolean hasDedicatedAnswerLines = lines.stream()
            .anyMatch(l -> answerLinePattern.matcher(l.trim()).find());
        System.out.println("Format detected – has dedicated Answer: lines: " + hasDedicatedAnswerLines);

        if (hasDedicatedAnswerLines) {
            // ── Format A: Q#: [question text]  …  Answer: [actual answer] ──────────
            int currentQNum = 0;
            for (String line : lines) {
                String trimmed = line.trim();

                if (skipPattern.matcher(trimmed).find()) {
                    System.out.println("Skipping header/metadata: " + trimmed);
                    continue;
                }
                if (trimmed.length() < 2 || trimmed.matches("^\\d+$")) continue;

                // "Answer: ..." line → this is the actual answer we want
                Matcher ansMatcher = answerLinePattern.matcher(trimmed);
                if (ansMatcher.find() && currentQNum > 0) {
                    String answer = ansMatcher.group(1).trim()
                        .replaceFirst("^[A-Da-d][.)\\s]\\s*", "").trim();
                    if (!answer.isEmpty()) {
                        answerKey.put(currentQNum, answer);
                        System.out.println("Q" + currentQNum + " -> " + answer);
                    }
                    continue;
                }

                // Q# header → update tracking number only (don't treat content as answer)
                Matcher qMatcher = questionHeaderPattern.matcher(trimmed);
                if (qMatcher.find()) {
                    try { currentQNum = Integer.parseInt(qMatcher.group(1)); }
                    catch (NumberFormatException ignored) {}
                    continue;
                }

                // Numbered list line inside a combined doc ("1. answer") — still capture it
                Matcher numMatcher = numberedPattern.matcher(trimmed);
                if (numMatcher.matches()) {
                    int qNum = Integer.parseInt(numMatcher.group(1));
                    String answer = numMatcher.group(2).trim()
                        .replaceFirst("^[A-Da-d][.)\\s]\\s*", "").trim();
                    if (!answer.isEmpty()) {
                        answerKey.put(qNum, answer);
                        System.out.println("Q" + qNum + " -> " + answer);
                    }
                }
            }

        } else {
            // ── Format B / C: Q#: [answer]  or  1. [answer] ─────────────────────────
            int lastQNum = 0;
            StringBuilder currentAnswer = new StringBuilder();

            for (String line : lines) {
                String trimmed = line.trim();

                if (skipPattern.matcher(trimmed).find()) {
                    System.out.println("Skipping header/metadata: " + trimmed);
                    continue;
                }
                if (trimmed.length() < 2 || trimmed.matches("^\\d+$")) continue;

                int qNum = 0;
                String answer = null;
                boolean isNewQ = false;

                // Q# (Easy): answer
                Matcher qMatcher = questionHeaderPattern.matcher(trimmed);
                if (qMatcher.find()) {
                    try { qNum = Integer.parseInt(qMatcher.group(1)); } catch (NumberFormatException e) { continue; }
                    answer = qMatcher.group(2).trim()
                        .replaceFirst("^[A-Da-d][.)\\s]\\s*", "").trim();
                    isNewQ = true;
                } else {
                    // "1. answer" or "1) answer"
                    Matcher numMatcher = numberedPattern.matcher(trimmed);
                    if (numMatcher.matches()) {
                        qNum = Integer.parseInt(numMatcher.group(1));
                        answer = numMatcher.group(2).trim()
                            .replaceFirst("^[A-Da-d][.)\\s]\\s*", "").trim();
                        isNewQ = true;
                    }
                }

                if (isNewQ) {
                    // Save previously accumulated answer
                    if (lastQNum > 0 && currentAnswer.length() > 0) {
                        String finalAnswer = currentAnswer.toString().trim();
                        if (!finalAnswer.isEmpty()) {
                            answerKey.put(lastQNum, finalAnswer);
                            System.out.println("Completed Q" + lastQNum + " -> " + finalAnswer);
                        }
                    }
                    lastQNum = qNum;
                    currentAnswer = new StringBuilder(answer != null ? answer : "");
                } else if (lastQNum > 0) {
                    // Multi-line answer continuation
                    currentAnswer.append(" ").append(trimmed);
                    answerKey.put(lastQNum, currentAnswer.toString().trim());
                    System.out.println("Appending to Q" + lastQNum + " -> " + currentAnswer.toString().trim());
                }
            }

            // Flush last entry
            if (lastQNum > 0 && currentAnswer.length() > 0) {
                String finalAnswer = currentAnswer.toString().trim();
                if (!finalAnswer.isEmpty()) {
                    answerKey.put(lastQNum, finalAnswer);
                    System.out.println("Completed Q" + lastQNum + " -> " + finalAnswer);
                }
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
     * 4. ID, Difficulty, Type, Question format
     */
    private CsvProcessResult processCsvExam(MultipartFile file, HttpSession session, 
                                        Map<Integer, String> externalAnswerKey) throws IOException {
        List<String> questionBlocks = new ArrayList<>();
        List<String> difficultyList = new ArrayList<>();
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
            
            // Detect if this is a mixed format CSV with Type column
            boolean isMixedFormat = headerLine.toLowerCase().contains("type") && 
                                   (headerLine.toLowerCase().contains("difficulty") || 
                                    headerLine.toLowerCase().contains("id"));
            
            System.out.println("Format detected: " + (isMixedFormat ? "Mixed (Multiple Choice + Open-Ended)" : "Standard"));
            
            // Process all rows using processCSVRow which handles both types
            // Process first line if it's not a header
            if (!hasHeader && headerLine != null && !headerLine.trim().isEmpty()) {
                processCSVRow(headerLine, 1, questionBlocks, difficultyList, answerKey);
            }
            
            String line;
            int questionNumber = hasHeader ? 1 : 2;
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                processCSVRow(line, questionNumber, questionBlocks, difficultyList, answerKey);
                questionNumber++;
            }
        }
        
        System.out.println("=== CSV EXAM PARSED: " + questionBlocks.size() + " questions ===");
        
        // Merge external answer key if provided and convert letter answers to text
        if (externalAnswerKey != null && !externalAnswerKey.isEmpty()) {
            System.out.println("Merging external answer key with " + externalAnswerKey.size() + " answers");
            
            // Convert letter answers (A, B, C, D) to actual choice text
            for (int i = 0; i < questionBlocks.size(); i++) {
                String question = questionBlocks.get(i);
                String answer = externalAnswerKey.get(i + 1);
                
                if (answer != null && !question.startsWith("[TEXT_INPUT]")) {
                    // Check if answer is a single letter (A, B, C, D)
                    String answerTrimmed = answer.trim();
                    if (answerTrimmed.length() == 1 && answerTrimmed.matches("[A-Da-d]")) {
                        // Convert letter to actual choice text
                        String actualAnswer = convertLetterToChoiceText(question, answerTrimmed);
                        if (actualAnswer != null) {
                            answerKey.put(i + 1, actualAnswer);
                            System.out.println("Q" + (i + 1) + " converted answer '" + answerTrimmed + "' to: " + actualAnswer);
                        } else {
                            answerKey.put(i + 1, answer);
                            System.out.println("WARNING: Q" + (i + 1) + " could not convert letter '" + answerTrimmed + "'");
                        }
                    } else {
                        // Answer is already text, use as-is
                        answerKey.put(i + 1, answer);
                    }
                } else {
                    // Text input or no answer
                    if (answer != null) {
                        answerKey.put(i + 1, answer);
                    }
                }
            }
        }
        
        // Shuffle questions with Fisher-Yates
        List<QuestionWithAnswer> questionsWithAnswers = new ArrayList<>();
        for (int i = 0; i < questionBlocks.size(); i++) {
            String question = questionBlocks.get(i);
            String answer = answerKey.get(i + 1);
            String difficulty = i < difficultyList.size() ? difficultyList.get(i) : "Medium";
            questionsWithAnswers.add(new QuestionWithAnswer(question, answer, difficulty, i + 1));
        }
        
        SecureRandom rand = new SecureRandom();
        Collections.shuffle(questionsWithAnswers, rand);
        
        // Rebuild with shuffled order
        questionBlocks.clear();
        difficultyList.clear();
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
            difficultyList.add(qa.difficulty);
            answerKey.put(i + 1, qa.answer);
            
            System.out.println("Shuffled Q" + (i + 1) + " (originally Q" + qa.originalNumber + ") [" + qa.difficulty + "] -> " + qa.answer);
        }
        
        session.setAttribute("correctAnswerKey", answerKey);
        return new CsvProcessResult(questionBlocks, difficultyList);
    }
    
    /**
     * Process a single CSV row - handles multiple formats
     */
    private void processCSVRow(String line, int questionNumber, 
                               List<String> questionBlocks, List<String> difficultyList, 
                               Map<Integer, String> answerKey) {
        String[] columns = parseCsvLine(line);
        
        // Format 4: ID, Difficulty, Type, Question (with embedded choices)
        // Example: 1,Easy,Multiple Choice,"What is...? (A) Choice1 (B) Choice2 (C) Choice3"
        if (columns.length == 4) {
            String difficulty = columns[1].trim();
            String type = columns[2].trim();
            String fullQuestion = columns[3].trim();
            
            // Store difficulty level
            difficultyList.add(difficulty);
            
            // Check if it's open-ended or essay
            if (type.equalsIgnoreCase("Open-Ended") || 
                type.equalsIgnoreCase("Open Ended") || 
                type.equalsIgnoreCase("Essay") ||
                type.equalsIgnoreCase("Open") ||
                type.equalsIgnoreCase("Text Input")) {
                questionBlocks.add("[TEXT_INPUT]" + fullQuestion);
                System.out.println("Parsed CSV Q" + questionNumber + " (Open-Ended/Essay) -> " + fullQuestion.substring(0, Math.min(50, fullQuestion.length())));
                return;
            }
            
            // Parse multiple choice with embedded choices
            // Check if question has embedded choices like (A) (B) (C) (D)
            if (fullQuestion.contains("(A)") && fullQuestion.contains("(B)")) {
                String questionPart;
                List<String> choices = new ArrayList<>();
                
                // Find where choices start
                int choicesStart = fullQuestion.indexOf("(A)");
                questionPart = fullQuestion.substring(0, choicesStart).trim();
                
                // Extract choices using regex
                Pattern choicePattern = Pattern.compile("\\([A-D]\\)\\s*([^(]+?)(?=\\s*\\([A-D]\\)|$)");
                java.util.regex.Matcher matcher = choicePattern.matcher(fullQuestion);
                
                // Extract all choices
                while (matcher.find()) {
                    String choice = matcher.group(1).trim();
                    if (!choice.isEmpty()) {
                        choices.add(choice);
                    }
                }
                
                // Build formatted question block if we found choices
                if (choices.size() >= 2) {
                    StringBuilder questionBlock = new StringBuilder();
                    questionBlock.append(questionPart).append("\n");
                    
                    char choiceLetter = 'A';
                    for (String choice : choices) {
                        questionBlock.append(choiceLetter).append(") ").append(choice).append("\n");
                        choiceLetter++;
                    }
                    
                    // Remove trailing newline
                    String formattedBlock = questionBlock.toString().trim();
                    questionBlocks.add(formattedBlock);
                    System.out.println("Parsed CSV Q" + questionNumber + " (Multiple Choice with " + choices.size() + " choices)");
                    return;
                }
            }
            
            // If no embedded choices found but type is Multiple Choice, log warning
            if (type.equalsIgnoreCase("Multiple Choice")) {
                System.out.println("WARNING: Q" + questionNumber + " marked as Multiple Choice but no embedded choices found!");
                System.out.println("Question text: " + fullQuestion);
            }
            
            // Fallback: treat as text input since no choices found
            questionBlocks.add("[TEXT_INPUT]" + fullQuestion);
            System.out.println("Parsed CSV Q" + questionNumber + " (Fallback to Text Input)");
            return;
        }
        
        // Format 1: Question, ChoiceA, ChoiceB, ChoiceC, ChoiceD, Answer
        if (columns.length >= 6) {
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
            // Infer difficulty automatically for this format
            String inferredDifficulty = inferDifficultyFromQuestion(questionText, "MULTIPLE_CHOICE");
            difficultyList.add(inferredDifficulty);
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
            // Infer difficulty automatically for this format
            String inferredDifficulty = inferDifficultyFromQuestion(questionText, "MULTIPLE_CHOICE");
            difficultyList.add(inferredDifficulty);
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
            
            // Infer difficulty automatically for this format
            String inferredDifficulty = inferDifficultyFromQuestion(fullText, "TEXT_INPUT");
            difficultyList.add(inferredDifficulty);
            if (correctAnswer != null) {
                answerKey.put(questionNumber, correctAnswer);
                System.out.println("Parsed CSV Q" + questionNumber + " (Format 3 with embedded answer) -> " + correctAnswer);
            } else {
                System.out.println("Parsed CSV Q" + questionNumber + " (Format 3 - no answer found)");
            }
            
        } else {
            System.out.println("WARNING: Skipping malformed CSV line (" + columns.length + " columns): " + line);
        }
    }

    /**
     * Infer a difficulty label (Easy / Medium / Hard) from question text when
     * the CSV format does not explicitly provide a difficulty column.
     *
     * Heuristic rules used (additive scoring model):
     * - Base on word count (shorter questions tend to be easier).
     * - Penalize for tricky keywords such as "NOT", "EXCEPT", "LEAST", etc.
     * - Increase difficulty when the question appears multi-step or numeric-heavy.
     * - Open-ended / text-input questions default towards Medium/Hard.
     */
    private String inferDifficultyFromQuestion(String rawText, String type) {
        if (rawText == null) {
            return "Medium";
        }

        String text = rawText.trim();
        if (text.isEmpty()) {
            return "Medium";
        }

        String lower = text.toLowerCase();

        int score = 0;

        // 1) Length / word-count based scoring
        String[] words = lower.split("\\s+");
        int wordCount = words.length;
        if (wordCount <= 8) {
            score += 0; // very short -> likely Easy
        } else if (wordCount <= 20) {
            score += 1; // normal length -> baseline Medium
        } else if (wordCount <= 40) {
            score += 2; // long questions -> leaning Medium/Hard
        } else {
            score += 3; // very long -> more likely Hard
        }

        // 2) Tricky logic keywords (negations, exceptions, etc.)
        String trickyPattern = ".*\\b(not|except|least|false|incorrect|never|all of the following|none of the above|all of the above)\\b.*";
        if (lower.matches(trickyPattern)) {
            score += 2;
        }

        // 3) Multi-step / reasoning indicators
        String reasoningPattern = ".*\\b(first|second|third|then|finally|therefore|consequently|if\\s+.*then)\\b.*";
        if (lower.matches(reasoningPattern)) {
            score += 1;
        }

        // 4) Numeric / formula style questions (common in math/physics)
        boolean hasNumber = lower.matches(".*\\d+.*");
        boolean hasOperator = lower.matches(".*(\\+|\\-|\\*|/|=|%|>|<) .*");
        if (hasNumber && hasOperator) {
            score += 1;
        }

        // 5) Question type bias: text input / open-ended tends to be harder
        if (type != null && type.equalsIgnoreCase("TEXT_INPUT")) {
            score += 1;
        }

        // Map score to difficulty band
        if (score <= 1) {
            return "Easy";
        } else if (score <= 3) {
            return "Medium";
        } else {
            return "Hard";
        }
    }
    
    /**
     * Convert letter answer (A, B, C, D) to actual choice text from question block
     */
    private String convertLetterToChoiceText(String questionBlock, String letter) {
        try {
            // Split question into lines
            String[] lines = questionBlock.split("\n");
            
            // Extract choices (lines that start with A), B), C), or D))
            List<String> choices = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.matches("^[A-Da-d]\\)\\s+.+")) {
                    // Extract choice text after the letter and parenthesis
                    String choiceText = line.replaceFirst("^[A-Da-d]\\)\\s+", "").trim();
                    choices.add(choiceText);
                }
            }
            
            // Convert letter to index (A=0, B=1, C=2, D=3)
            char letterUpper = letter.toUpperCase().charAt(0);
            int index = letterUpper - 'A';
            
            // Return choice at index
            if (index >= 0 && index < choices.size()) {
                return choices.get(index);
            } else {
                System.out.println("ERROR: Letter '" + letter + "' index " + index + " out of range for " + choices.size() + " choices");
                return null;
            }
            
        } catch (Exception e) {
            System.out.println("ERROR converting letter '" + letter + "' to choice text: " + e.getMessage());
            return null;
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
        // Create a unique uploads folder for images in this exam
        String examId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Path uploadsDir = Paths.get("uploads", "exam-images", examId);
        Files.createDirectories(uploadsDir);

        List<String> rawLines = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            int totalPages = document.getNumberOfPages();

            for (int pageIdx = 0; pageIdx < totalPages; pageIdx++) {
                // ── 1. Extract images from this page ──────────────────────
                List<String> pageImageUrls = new ArrayList<>();
                PDPage page = document.getPage(pageIdx);
                PDResources resources = page.getResources();
                if (resources != null) {
                    for (COSName xName : resources.getXObjectNames()) {
                        try {
                            var xObject = resources.getXObject(xName);
                            if (xObject instanceof PDImageXObject img) {
                                // Skip tiny images (logos, watermarks, decorative icons)
                                if (img.getWidth() > 60 && img.getHeight() > 60) {
                                    String imgFile = "p" + pageIdx + "_" + xName.getName() + ".png";
                                    Path imgPath = uploadsDir.resolve(imgFile);
                                    ImageIO.write(img.getImage(), "PNG", imgPath.toFile());
                                    pageImageUrls.add("/uploads/exam-images/" + examId + "/" + imgFile);
                                    System.out.println("Extracted image: " + imgFile + " (" + img.getWidth() + "x" + img.getHeight() + ")");
                                }
                            }
                        } catch (IOException e) {
                            System.out.println("Warning: Could not extract image " + xName.getName() + ": " + e.getMessage());
                        }
                    }
                }

                // ── 2. Emit a page-image marker (before the page's text lines) ──
                if (!pageImageUrls.isEmpty()) {
                    rawLines.add("__PAGE_IMAGES__:" + String.join("|", pageImageUrls));
                }

                // ── 3. Extract text for this page ─────────────────────────
                PDFTextStripper pageStripper = new PDFTextStripper();
                pageStripper.setStartPage(pageIdx + 1);
                pageStripper.setEndPage(pageIdx + 1);
                String pageText = pageStripper.getText(document);

                Arrays.stream(pageText.split("\\r?\\n"))
                      .filter(line -> !line.trim().isEmpty())
                      .map(this::normalizeEquationText)
                      .forEach(rawLines::add);
            }
        }

        System.out.println("=== PROCESSING EXAM PDF ===");
        System.out.println("Total lines (incl. markers): " + rawLines.size());

        // Debug: Print first 60 lines to understand the structure
        System.out.println("=== FIRST 60 LINES OF PDF ===");
        for (int i = 0; i < Math.min(60, rawLines.size()); i++) {
            System.out.println("Line " + i + ": " + rawLines.get(i));
        }
        System.out.println("=== END OF SAMPLE ===");

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

        // Images waiting to be attached to the next question encountered
        List<String> pendingImages = new ArrayList<>();

        for (String line : rawLines) {
            String trimmed = line.trim();

            // ── Handle page-image markers ──────────────────────────────────
            if (trimmed.startsWith("__PAGE_IMAGES__:")) {
                String imgList = trimmed.substring("__PAGE_IMAGES__:".length());
                for (String imgUrl : imgList.split("\\|")) {
                    if (!imgUrl.isBlank()) pendingImages.add(imgUrl.trim());
                }
                continue;
            }

            // Skip headers, page numbers, and metadata using improved pattern
            if (skipPattern.matcher(trimmed).find() || trimmed.isEmpty()) {
                System.out.println("Skipping: " + trimmed);
                continue;
            }

            // Detect new question: "1. text", "Q1: text", "Q1 (Easy): text", "Q1 (Medium): text"
            boolean isNewQuestion = trimmed.matches("^\\d+\\.\\s+.*")
                || trimmed.matches("(?i)^q\\s*\\d+\\s*(\\([^)]*\\))?\\s*:.*");
            if (isNewQuestion) {
                // Save previous question block if exists
                if (currentBlock.length() > 0) {
                    System.out.println("Processing block for Q" + (qID + 1) + ": " + currentBlock.toString().substring(0, Math.min(50, currentBlock.length())) + "...");
                    String processed = extractAnswerAndShuffle(currentBlock.toString(), rand, answerKey, qID);
                    if (!processed.isEmpty()) {
                        questionBlocks.add(processed);
                        System.out.println("Successfully processed Q" + (qID + 1));
                        qID++;
                    } else {
                        System.out.println("WARNING: Empty result for Q" + (qID + 1));
                    }
                }
                // Strip numeric/Q-prefix: "1. " or "Q1 (Easy): "
                String questionContent = trimmed
                    .replaceFirst("^\\d+\\.\\s+", "")
                    .replaceFirst("(?i)^q\\s*\\d+\\s*(\\([^)]*\\))?\\s*:\\s*", "");

                // ── Attach any pending images to this question ─────────────
                StringBuilder block = new StringBuilder(questionContent);
                for (String imgUrl : pendingImages) {
                    block.append("\n[IMG:").append(imgUrl).append("]");
                }
                pendingImages.clear();

                currentBlock = block;
            } else {
                // Add to current question block
                if (currentBlock.length() > 0) {
                    currentBlock.append("\n").append(trimmed);
                }
            }
        }
        
        // Don't forget the last question
        if (currentBlock.length() > 0) {
            System.out.println("Processing final block for Q" + (qID + 1) + ": " + currentBlock.toString().substring(0, Math.min(50, currentBlock.length())) + "...");
            String processed = extractAnswerAndShuffle(currentBlock.toString(), rand, answerKey, qID);
            if (!processed.isEmpty()) {
                questionBlocks.add(processed);
                System.out.println("Successfully processed Q" + (qID + 1));
                qID++;
            } else {
                System.out.println("WARNING: Empty result for final Q" + (qID + 1));
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
            
            questionsWithAnswers.add(new QuestionWithAnswer(question, answer, "Medium", i + 1));
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
        
        // Allow single-line questions (could be open-ended)
        if (lines.length == 0 || block.trim().isEmpty()) return "";

        String questionText = lines[0].trim();
        List<String> choices = new ArrayList<>();
        String correctAnswer = null;
        boolean isOpenEnded = false;
        boolean isEssay = false;
        boolean foundEmbeddedChoices = false;

        // Check if this is marked as open-ended or essay question
        if (questionText.toLowerCase().contains("[open-ended]") || 
            questionText.toLowerCase().contains("[text-input]") ||
            questionText.toLowerCase().contains("(open-ended)")) {
            isOpenEnded = true;
            questionText = questionText.replaceAll("(?i)\\[(open-ended|text-input)\\]|\\(open-ended\\)", "").trim();
        }
        
        if (questionText.toLowerCase().contains("[essay]") || 
            questionText.toLowerCase().contains("(essay)")) {
            isEssay = true;
            questionText = questionText.replaceAll("(?i)\\[(essay)\\]|\\(essay\\)", "").trim();
        }
        
        // Check if choices are embedded in the question text itself (e.g., "What is...? (A) HTML (B) SQL (C) CSS")
        if (!isOpenEnded && !isEssay && questionText.contains("(A)") && questionText.contains("(B)")) {
            // Extract question part before choices
            int choicesStart = questionText.indexOf("(A)");
            String pureQuestion = questionText.substring(0, choicesStart).trim();
            
            // Extract choices using regex
            Pattern choicePattern = Pattern.compile("\\([A-D]\\)\\s*([^(]+?)(?=\\s*\\([A-D]\\)|$)");
            java.util.regex.Matcher matcher = choicePattern.matcher(questionText);
            
            List<String> embeddedChoices = new ArrayList<>();
            while (matcher.find()) {
                String choice = matcher.group(1).trim();
                if (!choice.isEmpty()) {
                    embeddedChoices.add(choice);
                }
            }
            
            // If we found embedded choices, use them
            if (embeddedChoices.size() >= 2) {
                questionText = pureQuestion;
                choices.addAll(embeddedChoices);
                foundEmbeddedChoices = true;
                System.out.println("Detected embedded choices in Q" + (id + 1) + ": " + embeddedChoices.size() + " choices found");
            }
        }

        // Process remaining lines for traditional format (choices on separate lines)
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // ── Pass-through image markers — embed directly in question text ──
            if (line.startsWith("[IMG:") && line.endsWith("]")) {
                questionText = questionText + "\n" + line;
                continue;
            }

            // Check for question type markers in subsequent lines
            if (line.equalsIgnoreCase("Type: Open-Ended") || 
                line.equalsIgnoreCase("Type: Essay") ||
                line.equalsIgnoreCase("Type: Text Input")) {
                isOpenEnded = true;
                continue;
            }
            
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
            
            // Check if it's an answer choice (A), B), C), D) - only if we haven't found embedded choices
            if (!foundEmbeddedChoices && !line.isEmpty() && !line.equalsIgnoreCase("choices:") && !line.equalsIgnoreCase("options:")) {
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

        // Handle open-ended/essay questions (no multiple choices)
        if (isOpenEnded || isEssay || choices.isEmpty()) {
            // Mark as text-input question
            if (correctAnswer != null) {
                key.put(id + 1, correctAnswer);
            }
            System.out.println("Q" + (id + 1) + " treated as TEXT_INPUT (isOpenEnded=" + isOpenEnded + ", isEssay=" + isEssay + ", choices.size=" + choices.size() + ")");
            return "[TEXT_INPUT]" + questionText;
        }        
        // Convert answer letter to actual choice text BEFORE shuffling
        if (correctAnswer != null) {
            // Check if answer is just a single letter (A, B, C, D)
            if (correctAnswer.length() == 1 && correctAnswer.matches("[A-Da-d]")) {
                int answerIndex = Character.toUpperCase(correctAnswer.charAt(0)) - 'A';
                if (answerIndex >= 0 && answerIndex < choices.size()) {
                    correctAnswer = choices.get(answerIndex);
                    System.out.println("Q" + (id + 1) + " converted answer letter to text: " + correctAnswer);
                }
            }
            // Store the actual choice text as the answer
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

    // ─────────────────────────────────────────────────────────────────────
    // Math / LaTeX normalization helpers (used during PDF exam ingestion)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Convert Unicode math characters extracted from PDFs into LaTeX notation,
     * then auto-wrap the detected math segments in $...$ so MathJax can render them.
     * Lines that already contain LaTeX delimiters ($, \(, \[) are passed through unchanged.
     */
    private String normalizeEquationText(String text) {
        if (text == null || text.isEmpty()) return text;

        // Already wrapped – pass through
        if (text.contains("$") || text.contains("\\(") || text.contains("\\[")) return text;

        String original = text;

        // ── Superscripts ──────────────────────────────────────────────────
        text = text.replace("⁰","^{0}").replace("¹","^{1}").replace("²","^{2}")
                   .replace("³","^{3}").replace("⁴","^{4}").replace("⁵","^{5}")
                   .replace("⁶","^{6}").replace("⁷","^{7}").replace("⁸","^{8}")
                   .replace("⁹","^{9}").replace("ⁿ","^{n}").replace("ˣ","^{x}");

        // ── Subscripts ───────────────────────────────────────────────────
        text = text.replace("₀","_{0}").replace("₁","_{1}").replace("₂","_{2}")
                   .replace("₃","_{3}").replace("₄","_{4}").replace("₅","_{5}")
                   .replace("₆","_{6}").replace("₇","_{7}").replace("₈","_{8}")
                   .replace("₉","_{9}").replace("ₙ","_{n}");

        // ── Unicode fractions ────────────────────────────────────────────
        text = text.replace("½","\\frac{1}{2}").replace("⅓","\\frac{1}{3}")
                   .replace("⅔","\\frac{2}{3}").replace("¼","\\frac{1}{4}")
                   .replace("¾","\\frac{3}{4}").replace("⅛","\\frac{1}{8}")
                   .replace("⅜","\\frac{3}{8}").replace("⅝","\\frac{5}{8}")
                   .replace("⅞","\\frac{7}{8}");

        // ── Radical / root ───────────────────────────────────────────────
        // Match √N, √x, √(expr) before the bare √ fallback
        text = text.replaceAll("√([0-9]+)",       "\\\\sqrt{$1}");
        text = text.replaceAll("√([a-zA-Z])",     "\\\\sqrt{$1}");
        text = text.replaceAll("√\\(([^)]+)\\)",  "\\\\sqrt{$1}");
        text = text.replace   ("√",                "\\sqrt{}");
        text = text.replaceAll("∛([0-9]+)",        "\\\\sqrt[3]{$1}");
        text = text.replaceAll("∛([a-zA-Z])",      "\\\\sqrt[3]{$1}");
        text = text.replace   ("∛",                "\\sqrt[3]{}");

        // ── Operators ────────────────────────────────────────────────────
        text = text.replace("×","\\times ").replace("÷","\\div ")
                   .replace("±","\\pm ").replace("·","\\cdot ");

        // ── Relations ────────────────────────────────────────────────────
        text = text.replace("≤","\\leq ").replace("≥","\\geq ")
                   .replace("≠","\\neq ").replace("≈","\\approx ")
                   .replace("≡","\\equiv ").replace("∝","\\propto ");

        // ── Greek (lower) ─────────────────────────────────────────────────
        text = text.replace("α","\\alpha").replace("β","\\beta")
                   .replace("γ","\\gamma").replace("δ","\\delta")
                   .replace("ε","\\varepsilon").replace("θ","\\theta")
                   .replace("λ","\\lambda").replace("μ","\\mu")
                   .replace("π","\\pi").replace("σ","\\sigma")
                   .replace("τ","\\tau").replace("φ","\\phi")
                   .replace("χ","\\chi").replace("ψ","\\psi")
                   .replace("ω","\\omega");

        // ── Greek (upper) ─────────────────────────────────────────────────
        text = text.replace("Δ","\\Delta").replace("Σ","\\Sigma")
                   .replace("Γ","\\Gamma").replace("Λ","\\Lambda")
                   .replace("Ω","\\Omega").replace("Π","\\Pi");

        // ── Calculus & set notation ───────────────────────────────────────
        text = text.replace("∑","\\sum").replace("∏","\\prod")
                   .replace("∫","\\int").replace("∂","\\partial")
                   .replace("∇","\\nabla").replace("∈","\\in")
                   .replace("∉","\\notin").replace("∪","\\cup")
                   .replace("∩","\\cap").replace("⊂","\\subset")
                   .replace("∅","\\emptyset").replace("∞","\\infty");

        // ── Number sets ───────────────────────────────────────────────────
        text = text.replace("ℝ","\\mathbb{R}").replace("ℤ","\\mathbb{Z}")
                   .replace("ℕ","\\mathbb{N}").replace("ℚ","\\mathbb{Q}");

        // If nothing changed, no wrapping needed
        if (text.equals(original)) return text;

        // Wrap detected LaTeX segments in $...$
        return wrapLatexSegments(text);
    }

    /**
     * Scan text for LaTeX command / sub-superscript segments and wrap each in $...$.
     * Surrounding plain-language words are left unchanged.
     */
    private String wrapLatexSegments(String text) {
        if (text.contains("$") || text.contains("\\(")) return text;
        Matcher m = LATEX_SEGMENT.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String seg = m.group().trim();
            if (!seg.isEmpty()) {
                m.appendReplacement(sb, Matcher.quoteReplacement("$" + seg + "$"));
            }
        }
        m.appendTail(sb);
        return sb.toString();
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
            // Clean up question text: remove [TEXT_INPUT] and difficulty markers
            String cleanQuestion = question
                .replaceAll("\\[TEXT_INPUT\\]", "")
                .replaceAll("\\[(Easy|Medium|Hard|Essay|Open-Ended|Open Ended)\\]", "")
                .trim();
            
            document.add(new Paragraph(questionNumber + ". " + cleanQuestion.replace("\n", "\n   ")));
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
            // Clean up question text: remove [TEXT_INPUT] and difficulty markers
            String cleanQuestion = question
                .replaceAll("\\[TEXT_INPUT\\]", "")
                .replaceAll("\\[(Easy|Medium|Hard|Essay|Open-Ended|Open Ended)\\]", "")
                .trim();
            
            XWPFParagraph questionPara = document.createParagraph();
            XWPFRun questionRun = questionPara.createRun();
            
            String[] lines = cleanQuestion.split("\n");
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
    
    /**
     * Export shuffled exam with answers (PDF)
     */
    @GetMapping("/export-exam-with-answers/pdf")
    public ResponseEntity<byte[]> exportExamWithAnswersPdf(@RequestParam String examId) throws DocumentException, IOException {
        UploadedExam exam = uploadedExams.get(examId);
        
        if (exam == null) {
            return ResponseEntity.notFound().build();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, baos);
        document.open();

        // --- Fonts ---
        Font titleFont    = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font subFont      = new Font(Font.HELVETICA, 11, Font.NORMAL);
        Font qLabelFont   = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font qTextFont    = new Font(Font.HELVETICA, 11, Font.NORMAL);
        Font ansLabelFont = new Font(Font.HELVETICA, 11, Font.BOLD);
        Font ansTextFont  = new Font(Font.HELVETICA, 11, Font.NORMAL);
        Font diffFont     = new Font(Font.HELVETICA, 10, Font.ITALIC);

        // --- Title block ---
        Paragraph title = new Paragraph(exam.getExamName() + " — WITH ANSWER KEY", titleFont);
        title.setAlignment(Paragraph.ALIGN_CENTER);
        title.setSpacingAfter(4f);
        document.add(title);

        Paragraph sub1 = new Paragraph("Subject: " + exam.getSubject(), subFont);
        sub1.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(sub1);

        Paragraph sub2 = new Paragraph(
            "Activity Type: " + exam.getActivityType() +
            "   |   Total Questions: " + exam.getQuestions().size(), subFont);
        sub2.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(sub2);

        Paragraph sub3 = new Paragraph(
            "Generated: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), subFont);
        sub3.setAlignment(Paragraph.ALIGN_CENTER);
        sub3.setSpacingAfter(14f);
        document.add(sub3);

        // Divider line (dashes)
        Paragraph divider = new Paragraph("─".repeat(70), diffFont);
        divider.setSpacingAfter(10f);
        document.add(divider);

        // --- Answer key body ---
        for (int i = 0; i < exam.getQuestions().size(); i++) {
            // ── Q-number + difficulty on its own bold line ──
            Paragraph qHeader = new Paragraph();
            qHeader.add(new Chunk(
                "Q" + (i + 1) + "  [" + exam.getDifficulties().get(i) + "]",
                qLabelFont));
            qHeader.setSpacingBefore(10f);
            qHeader.setSpacingAfter(2f);
            document.add(qHeader);

            // ── Question text ──
            Paragraph qText = new Paragraph(exam.getQuestions().get(i), qTextFont);
            qText.setIndentationLeft(16f);
            qText.setSpacingAfter(4f);
            document.add(qText);

            // ── Answer: label (bold) + answer value (normal) on the SAME paragraph ──
            Paragraph answerPara = new Paragraph();
            answerPara.add(new Chunk("Answer: ", ansLabelFont));
            answerPara.add(new Chunk(
                exam.getAnswerKey().getOrDefault(i + 1, "N/A"), ansTextFont));
            answerPara.setIndentationLeft(16f);
            answerPara.setSpacingAfter(4f);
            document.add(answerPara);
        }

        document.close();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", exam.getExamName().replaceAll("\\s+", "_") + "_with_answers.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(baos.toByteArray());
    }
    
    /**
     * Export shuffled exam with answers (CSV)
     */
    @GetMapping("/export-exam-with-answers/csv")
    public ResponseEntity<byte[]> exportExamWithAnswersCsv(@RequestParam String examId) throws IOException {
        UploadedExam exam = uploadedExams.get(examId);
        
        if (exam == null) {
            return ResponseEntity.notFound().build();
        }

        StringBuilder csv = new StringBuilder();
        // CSV Header
        csv.append("Question Number,Question Text,Difficulty,Correct Answer\n");
        
        // Add each question with answer
        for (int i = 0; i < exam.getQuestions().size(); i++) {
            // Flatten newlines so multi-choice options stay in one row (no misalignment in spreadsheets)
            String questionText = exam.getQuestions().get(i)
                .replace("\r\n", " | ")
                .replace("\r", " | ")
                .replace("\n", " | ")
                .replaceAll("\\s*\\|\\s*$", "") // trim trailing separator
                .replaceAll("\\s{2,}", " ")      // collapse extra spaces
                .trim();
            
            csv.append(i + 1).append(",");
            csv.append(escapeCSV(questionText)).append(",");
            csv.append(escapeCSV(exam.getDifficulties().get(i))).append(",");
            csv.append(escapeCSV(exam.getAnswerKey().getOrDefault(i + 1, "N/A")));
            csv.append("\n");
        }
        
        byte[] csvBytes = csv.toString().getBytes("UTF-8");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", exam.getExamName().replaceAll("\\s+", "_") + "_with_answers.csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }
    
    /**
     * Export shuffled exam with answers (DOCX)
     */
    @GetMapping("/export-exam-with-answers/docx")
    public ResponseEntity<byte[]> exportExamWithAnswersDocx(@RequestParam String examId) throws IOException {
        UploadedExam exam = uploadedExams.get(examId);
        
        if (exam == null) {
            return ResponseEntity.notFound().build();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XWPFDocument document = new XWPFDocument();

        // Add title
        XWPFParagraph titlePara = document.createParagraph();
        titlePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText(exam.getExamName() + " - WITH ANSWER KEY");
        titleRun.setBold(true);
        titleRun.setFontSize(18);

        // Add metadata
        document.createParagraph().createRun().addBreak();
        XWPFParagraph metaPara = document.createParagraph();
        XWPFRun metaRun = metaPara.createRun();
        metaRun.setText("Subject: " + exam.getSubject());
        metaRun.addBreak();
        metaRun.setText("Activity Type: " + exam.getActivityType());
        metaRun.addBreak();
        metaRun.setText("Total Questions: " + exam.getQuestions().size());
        metaRun.addBreak();
        metaRun.setText("Generated: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        metaRun.addBreak();
        metaRun.addBreak();

        // Add questions with answers
        for (int i = 0; i < exam.getQuestions().size(); i++) {
            XWPFParagraph qPara = document.createParagraph();
            XWPFRun qRun = qPara.createRun();
            qRun.setText("Question " + (i + 1) + ":");
            qRun.setBold(true);
            qRun.addBreak();
            
            qRun = qPara.createRun();
            qRun.setText(exam.getQuestions().get(i));
            qRun.addBreak();
            
            XWPFRun diffRun = qPara.createRun();
            diffRun.setText("Difficulty: " + exam.getDifficulties().get(i));
            diffRun.setItalic(true);
            diffRun.addBreak();
            
            XWPFRun ansRun = qPara.createRun();
            ansRun.setText("Answer: " + exam.getAnswerKey().getOrDefault(i + 1, "N/A"));
            ansRun.setColor("008000"); // Green color for answer
            ansRun.setBold(true);
            ansRun.addBreak();
            ansRun.addBreak();
        }

        document.write(baos);
        document.close();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDispositionFormData("attachment", exam.getExamName().replaceAll("\\s+", "_") + "_with_answers.docx");

        return ResponseEntity.ok()
                .headers(headers)
                .body(baos.toByteArray());
    }
    
    @GetMapping("/download-results")
    public ResponseEntity<byte[]> downloadResults() throws IOException {
        List<ExamSubmission> submissions = examSubmissionRepository.findAll();
        
        // Sort by latest first (null-safe)
        submissions.sort(Comparator.comparing(ExamSubmission::getSubmittedAt, 
                                             Comparator.nullsLast(Comparator.reverseOrder())));
        
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
    
    /**
     * Teacher grading page - view submissions needing manual grading
     */
    @GetMapping("/grade-submissions")
    public String gradeSubmissions(Model model, java.security.Principal principal) {
        String teacherEmail = principal.getName();
        
        // Get all submissions from enrolled students
        List<EnrolledStudent> enrolledStudents = enrolledStudentRepository.findByTeacherEmail(teacherEmail);
        List<String> studentEmails = enrolledStudents.stream()
                .map(EnrolledStudent::getStudentEmail)
                .collect(Collectors.toList());
        
        // Get all submissions for these students
        List<ExamSubmission> allSubmissions = examSubmissionRepository.findAll().stream()
                .filter(s -> studentEmails.contains(s.getStudentEmail()))
                .sorted(Comparator.comparing(ExamSubmission::getSubmittedAt, 
                                            Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        
        // Separate pending and graded
        List<ExamSubmission> pendingGrading = allSubmissions.stream()
                .filter(s -> !s.isGraded())
                .collect(Collectors.toList());
        
        List<ExamSubmission> graded = allSubmissions.stream()
                .filter(ExamSubmission::isGraded)
                .collect(Collectors.toList());
        
        model.addAttribute("pendingGrading", pendingGrading);
        model.addAttribute("gradedSubmissions", graded);
        model.addAttribute("totalPending", pendingGrading.size());
        
        return "teacher-grade-submissions";
    }
    
    /**
     * View specific submission for grading
     */
    @GetMapping("/grade/{submissionId}")
    public String gradeSubmission(@PathVariable Long submissionId, Model model) {
        Optional<ExamSubmission> submissionOpt = examSubmissionRepository.findById(submissionId);
        
        if (submissionOpt.isEmpty()) {
            return "redirect:/teacher/grade-submissions";
        }
        
        ExamSubmission submission = submissionOpt.get();
        
        // Parse answer details to show individual answers
        List<Map<String, Object>> answerDetails = new ArrayList<>();
        if (submission.getAnswerDetailsJson() != null && !submission.getAnswerDetailsJson().isEmpty()) {
            String[] details = submission.getAnswerDetailsJson().split(";");
            for (String detail : details) {
                if (!detail.trim().isEmpty()) {
                    String[] parts = detail.split("\\|");
                    if (parts.length >= 4) {
                        Map<String, Object> detailMap = new HashMap<>();
                        detailMap.put("questionNumber", Integer.parseInt(parts[0]));
                        detailMap.put("studentAnswer", parts[1]);
                        detailMap.put("correctAnswer", parts[2]);
                        detailMap.put("isCorrect", Boolean.parseBoolean(parts[3]));
                        
                        // Check if it's a text input question (might need manual grading)
                        boolean isTextInput = parts[1].length() > 50 || !parts[1].matches("[A-D]\\).+");
                        detailMap.put("isTextInput", isTextInput);
                        detailMap.put("needsManualGrade", isTextInput && !Boolean.parseBoolean(parts[3]));
                        
                        answerDetails.add(detailMap);
                    }
                }
            }
        }
        
        // Count questions needing manual grading
        long textInputCount = answerDetails.stream()
                .filter(d -> (Boolean) d.getOrDefault("needsManualGrade", false))
                .count();
        
        model.addAttribute("submission", submission);
        model.addAttribute("answerDetails", answerDetails);
        model.addAttribute("textInputCount", textInputCount);
        model.addAttribute("currentManualScore", submission.getManualScore() != null ? submission.getManualScore() : 0);
        
        return "teacher-grade-exam";
    }
    
    /**
     * Save manual grade and finalize score
     */
    @PostMapping("/finalize-grade")
    public String finalizeGrade(@RequestParam Long submissionId,
                               @RequestParam(required = false, defaultValue = "0") Integer manualScore,
                               @RequestParam(required = false) String teacherComments) {
        Optional<ExamSubmission> submissionOpt = examSubmissionRepository.findById(submissionId);
        
        if (submissionOpt.isPresent()) {
            ExamSubmission submission = submissionOpt.get();
            submission.setManualScore(manualScore);
            submission.setGraded(true);
            submission.setGradedAt(java.time.LocalDateTime.now());
            submission.setTeacherComments(teacherComments);
            
            // Update percentage to include manual score
            int totalScore = submission.getScore() + manualScore;
            double newPercentage = (totalScore * 100.0) / submission.getTotalQuestions();
            submission.setPercentage(newPercentage);
            
            examSubmissionRepository.save(submission);
            
            System.out.println("✅ Teacher finalized grade for submission #" + submissionId);
            System.out.println("   Auto Score: " + submission.getScore());
            System.out.println("   Manual Score: " + manualScore);
            System.out.println("   Final Score: " + totalScore + "/" + submission.getTotalQuestions());
            System.out.println("   Final Percentage: " + String.format("%.2f%%", newPercentage));
        }
        
        return "redirect:/teacher/grade-submissions";
    }
    
    /**
     * View list of enrolled students with result statistics
     */
    @GetMapping("/view-students-list")
    public String viewStudentsList(Model model, java.security.Principal principal) {
        String teacherEmail = principal.getName();

        // Get all enrolled students for this teacher
        List<EnrolledStudent> enrolledStudents = enrolledStudentRepository.findByTeacherEmail(teacherEmail);

        // Create student list with statistics + full submission list + aggregated analytics
        List<Map<String, Object>> studentList = new ArrayList<>();

        for (EnrolledStudent enrolled : enrolledStudents) {
            String studentEmail = enrolled.getStudentEmail();

            // Get all submissions for this student, newest first
            List<ExamSubmission> submissions = examSubmissionRepository.findByStudentEmail(studentEmail)
                    .stream()
                    .sorted((a, b) -> {
                        if (a.getSubmittedAt() == null && b.getSubmittedAt() == null) return 0;
                        if (a.getSubmittedAt() == null) return 1;
                        if (b.getSubmittedAt() == null) return -1;
                        return b.getSubmittedAt().compareTo(a.getSubmittedAt());
                    })
                    .collect(Collectors.toList());

            // Calculate grade statistics
            int totalSubmissions = submissions.size();
            long gradedCount   = submissions.stream().filter(ExamSubmission::isGraded).count();
            long pendingCount  = submissions.stream().filter(s -> !s.isGraded()).count();
            long releasedCount = submissions.stream().filter(ExamSubmission::isResultsReleased).count();

            double averageScore = 0.0;
            if (gradedCount > 0) {
                averageScore = submissions.stream()
                        .filter(ExamSubmission::isGraded)
                        .mapToDouble(ExamSubmission::getFinalPercentage)
                        .average()
                        .orElse(0.0);
            }

            // Aggregate Random Forest analytics from graded submissions
            List<ExamSubmission> gradedSubs = submissions.stream()
                    .filter(ExamSubmission::isGraded)
                    .collect(Collectors.toList());

            double avgTopicMastery        = gradedSubs.stream().mapToDouble(ExamSubmission::getTopicMastery).average().orElse(0.0);
            double avgDifficultyResilience = gradedSubs.stream().mapToDouble(ExamSubmission::getDifficultyResilience).average().orElse(0.0);
            double avgAccuracy            = gradedSubs.stream().mapToDouble(ExamSubmission::getAccuracy).average().orElse(0.0);
            double avgTimeEfficiency      = gradedSubs.stream().mapToDouble(ExamSubmission::getTimeEfficiency).average().orElse(0.0);
            double avgConfidence          = gradedSubs.stream().mapToDouble(ExamSubmission::getConfidence).average().orElse(0.0);

            // Most recent performance category (from latest graded submission)
            String latestCategory = gradedSubs.isEmpty() ? "N/A"
                    : (gradedSubs.get(0).getPerformanceCategory() != null
                       ? gradedSubs.get(0).getPerformanceCategory() : "N/A");

            Map<String, Object> studentData = new HashMap<>();
            studentData.put("studentEmail", studentEmail);
            studentData.put("studentName", enrolled.getStudentName());
            studentData.put("totalSubmissions", totalSubmissions);
            studentData.put("gradedCount", gradedCount);
            studentData.put("pendingCount", pendingCount);
            studentData.put("releasedCount", releasedCount);
            studentData.put("averageScore", String.format("%.1f", averageScore));
            studentData.put("submissions", submissions);
            // Analytics
            studentData.put("avgTopicMastery",         String.format("%.1f", avgTopicMastery));
            studentData.put("avgDifficultyResilience",  String.format("%.1f", avgDifficultyResilience));
            studentData.put("avgAccuracy",              String.format("%.1f", avgAccuracy));
            studentData.put("avgTimeEfficiency",        String.format("%.1f", avgTimeEfficiency));
            studentData.put("avgConfidence",            String.format("%.1f", avgConfidence));
            studentData.put("latestCategory",           latestCategory);
            studentData.put("hasAnalytics",             !gradedSubs.isEmpty());

            studentList.add(studentData);
        }

        model.addAttribute("studentList", studentList);
        model.addAttribute("totalStudents", studentList.size());

        return "teacher-view-students-list";
    }
    
    /**
     * View all results for a specific student
     */
    @GetMapping("/view-student-results/{email}")
    public String viewStudentResults(@PathVariable String email, Model model, java.security.Principal principal) {
        String teacherEmail = principal.getName();
        
        // Verify this student is enrolled with this teacher
        List<EnrolledStudent> enrolledStudents = enrolledStudentRepository.findByTeacherEmail(teacherEmail);
        boolean isEnrolled = enrolledStudents.stream()
                .anyMatch(e -> e.getStudentEmail().equals(email));
        
        if (!isEnrolled) {
            return "redirect:/teacher/view-students-list";
        }
        
        // Get all submissions for this student
        List<ExamSubmission> submissions = examSubmissionRepository.findByStudentEmail(email)
                .stream()
                .sorted((a, b) -> b.getSubmittedAt().compareTo(a.getSubmittedAt()))
                .collect(Collectors.toList());
        
        // Calculate statistics
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSubmissions", submissions.size());
        stats.put("gradedCount", submissions.stream().filter(ExamSubmission::isGraded).count());
        stats.put("pendingCount", submissions.stream().filter(s -> !s.isGraded()).count());
        stats.put("releasedCount", submissions.stream().filter(ExamSubmission::isResultsReleased).count());
        
        if (!submissions.isEmpty()) {
            double avgScore = submissions.stream()
                    .filter(ExamSubmission::isGraded)
                    .mapToInt(ExamSubmission::getFinalScore)
                    .average()
                    .orElse(0.0);
            double avgPercentage = submissions.stream()
                    .filter(ExamSubmission::isGraded)
                    .mapToDouble(ExamSubmission::getFinalPercentage)
                    .average()
                    .orElse(0.0);
            stats.put("averageScore", String.format("%.1f", avgScore));
            stats.put("averagePercentage", String.format("%.1f", avgPercentage));
        } else {
            stats.put("averageScore", "0.0");
            stats.put("averagePercentage", "0.0");
        }
        
        // Find student name
        String studentName = enrolledStudents.stream()
                .filter(e -> e.getStudentEmail().equals(email))
                .map(EnrolledStudent::getStudentName)
                .findFirst()
                .orElse(email);
        
        model.addAttribute("studentEmail", email);
        model.addAttribute("studentName", studentName);
        model.addAttribute("submissions", submissions);
        model.addAttribute("stats", stats);
        model.addAttribute("studentSummaryList", new ArrayList<>());
        
        return "teacher-view-results";
    }
    
    /**
     * View all quiz/exam results taken - shows all submissions from enrolled students
     */
    @GetMapping("/view-results")
    public String viewResults(Model model, 
                             @RequestParam(required = false) String examFilter,
                             @RequestParam(required = false) String subjectFilter,
                             @RequestParam(required = false) String statusFilter,
                             @RequestParam(required = false) String studentFilter,
                             java.security.Principal principal) {
        String teacherEmail = principal.getName();
        
        // Get all enrolled students for this teacher
        List<EnrolledStudent> enrolledStudents = enrolledStudentRepository.findByTeacherEmail(teacherEmail);
        List<String> studentEmails = enrolledStudents.stream()
                .map(EnrolledStudent::getStudentEmail)
                .collect(Collectors.toList());
        
        // Get all submissions for enrolled students
        List<ExamSubmission> allSubmissions = examSubmissionRepository.findAll().stream()
                .filter(s -> studentEmails.contains(s.getStudentEmail()))
                .sorted(Comparator.comparing(ExamSubmission::getSubmittedAt, 
                                            Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        
        // Apply filters
        List<ExamSubmission> filteredSubmissions = allSubmissions;
        
        if (examFilter != null && !examFilter.isEmpty()) {
            filteredSubmissions = filteredSubmissions.stream()
                    .filter(s -> s.getExamName().equalsIgnoreCase(examFilter))
                    .collect(Collectors.toList());
        }
        
        if (subjectFilter != null && !subjectFilter.isEmpty()) {
            filteredSubmissions = filteredSubmissions.stream()
                    .filter(s -> s.getSubject().equalsIgnoreCase(subjectFilter))
                    .collect(Collectors.toList());
        }
        
        if (studentFilter != null && !studentFilter.isEmpty()) {
            filteredSubmissions = filteredSubmissions.stream()
                    .filter(s -> s.getStudentEmail().equalsIgnoreCase(studentFilter))
                    .collect(Collectors.toList());
        }
        
        if (statusFilter != null && !statusFilter.isEmpty()) {
            switch (statusFilter.toLowerCase()) {
                case "graded":
                    filteredSubmissions = filteredSubmissions.stream()
                            .filter(ExamSubmission::isGraded)
                            .collect(Collectors.toList());
                    break;
                case "pending":
                    filteredSubmissions = filteredSubmissions.stream()
                            .filter(s -> !s.isGraded())
                            .collect(Collectors.toList());
                    break;
                case "released":
                    filteredSubmissions = filteredSubmissions.stream()
                            .filter(ExamSubmission::isResultsReleased)
                            .collect(Collectors.toList());
                    break;
                case "unreleased":
                    filteredSubmissions = filteredSubmissions.stream()
                            .filter(s -> !s.isResultsReleased())
                            .collect(Collectors.toList());
                    break;
            }
        }
        
        // Calculate statistics
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSubmissions", filteredSubmissions.size());
        stats.put("gradedCount", filteredSubmissions.stream().filter(ExamSubmission::isGraded).count());
        stats.put("pendingCount", filteredSubmissions.stream().filter(s -> !s.isGraded()).count());
        stats.put("releasedCount", filteredSubmissions.stream().filter(ExamSubmission::isResultsReleased).count());
        
        if (!filteredSubmissions.isEmpty()) {
            double avgScore = filteredSubmissions.stream()
                    .filter(ExamSubmission::isGraded)
                    .mapToInt(ExamSubmission::getFinalScore)
                    .average()
                    .orElse(0.0);
            double avgPercentage = filteredSubmissions.stream()
                    .filter(ExamSubmission::isGraded)
                    .mapToDouble(ExamSubmission::getFinalPercentage)
                    .average()
                    .orElse(0.0);
            stats.put("averageScore", String.format("%.1f", avgScore));
            stats.put("averagePercentage", String.format("%.1f", avgPercentage));
        } else {
            stats.put("averageScore", "0.0");
            stats.put("averagePercentage", "0.0");
        }
        
        // Get unique exams, subjects, and students for filter dropdowns
        List<String> uniqueExams = allSubmissions.stream()
                .map(ExamSubmission::getExamName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        List<String> uniqueSubjects = allSubmissions.stream()
                .map(ExamSubmission::getSubject)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        List<String> uniqueStudents = allSubmissions.stream()
                .map(ExamSubmission::getStudentEmail)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        model.addAttribute("submissions", filteredSubmissions);
        model.addAttribute("stats", stats);
        model.addAttribute("uniqueExams", uniqueExams);
        model.addAttribute("uniqueSubjects", uniqueSubjects);
        model.addAttribute("uniqueStudents", uniqueStudents);
        model.addAttribute("examFilter", examFilter);
        model.addAttribute("subjectFilter", subjectFilter);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("studentFilter", studentFilter);

        // Build per-student summary for the Students tab
        List<Map<String, Object>> studentSummaryList = new ArrayList<>();
        Map<String, List<ExamSubmission>> submissionsByStudent = allSubmissions.stream()
                .collect(Collectors.groupingBy(ExamSubmission::getStudentEmail));

        for (EnrolledStudent enrolled : enrolledStudents) {
            String sEmail = enrolled.getStudentEmail();
            List<ExamSubmission> subs = submissionsByStudent.getOrDefault(sEmail, new ArrayList<>());

            long gradedCnt   = subs.stream().filter(ExamSubmission::isGraded).count();
            long pendingCnt  = subs.stream().filter(s -> !s.isGraded()).count();
            long releasedCnt = subs.stream().filter(ExamSubmission::isResultsReleased).count();
            double avgPct    = subs.stream()
                    .filter(ExamSubmission::isGraded)
                    .mapToDouble(ExamSubmission::getFinalPercentage)
                    .average()
                    .orElse(0.0);

            Map<String, Object> entry = new HashMap<>();
            entry.put("studentEmail", sEmail);
            entry.put("studentName",  enrolled.getStudentName());
            entry.put("subjectName",  enrolled.getSubjectName());
            entry.put("totalSubmissions", subs.size());
            entry.put("gradedCount",   gradedCnt);
            entry.put("pendingCount",  pendingCnt);
            entry.put("releasedCount", releasedCnt);
            entry.put("averageScore",  String.format("%.1f", avgPct));
            entry.put("submissions",   subs);   // full list for accordion
            studentSummaryList.add(entry);
        }

        // Sort: students with most submissions first
        studentSummaryList.sort((a, b) ->
                Integer.compare((Integer) b.get("totalSubmissions"), (Integer) a.get("totalSubmissions")));

        model.addAttribute("studentSummaryList", studentSummaryList);
        
        return "teacher-view-results";
    }
    
    /**
     * View detailed result for a specific submission
     */
    @GetMapping("/view-result/{submissionId}")
    public String viewStudentResult(@PathVariable Long submissionId, Model model) {
        Optional<ExamSubmission> submissionOpt = examSubmissionRepository.findById(submissionId);
        
        if (submissionOpt.isEmpty()) {
            return "redirect:/teacher/view-results";
        }
        
        ExamSubmission submission = submissionOpt.get();
        
        // Parse answer details
        List<Map<String, Object>> answerDetails = new ArrayList<>();
        if (submission.getAnswerDetailsJson() != null && !submission.getAnswerDetailsJson().isEmpty()) {
            String[] details = submission.getAnswerDetailsJson().split(";");
            for (String detail : details) {
                if (!detail.trim().isEmpty()) {
                    String[] parts = detail.split("\\|");
                    if (parts.length >= 4) {
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
        
        // Calculate question statistics
        int totalQuestions = answerDetails.size();
        int correctAnswers = (int) answerDetails.stream()
                .filter(d -> (Boolean) d.get("isCorrect"))
                .count();
        int incorrectAnswers = totalQuestions - correctAnswers;
        
        model.addAttribute("submission", submission);
        model.addAttribute("answerDetails", answerDetails);
        model.addAttribute("totalQuestions", totalQuestions);
        model.addAttribute("correctAnswers", correctAnswers);
        model.addAttribute("incorrectAnswers", incorrectAnswers);
        
        return "teacher-view-student-result";
    }
    
    /**
     * Toggle result release status for a specific submission
     */
    @PostMapping("/toggle-result-release/{submissionId}")
    public String toggleResultRelease(@PathVariable Long submissionId, 
                                      @RequestParam(required = false) String redirectTo) {
        Optional<ExamSubmission> submissionOpt = examSubmissionRepository.findById(submissionId);
        
        if (submissionOpt.isPresent()) {
            ExamSubmission submission = submissionOpt.get();
            
            // Toggle the release status
            if (submission.isResultsReleased()) {
                submission.setResultsReleased(false);
                submission.setReleasedAt(null);
            } else {
                submission.setResultsReleased(true);
                submission.setReleasedAt(java.time.LocalDateTime.now());
            }
            
            examSubmissionRepository.save(submission);
        }
        
        // Redirect back to the appropriate page
        if ("detail".equals(redirectTo)) {
            return "redirect:/teacher/view-result/" + submissionId;
        }
        return "redirect:/teacher/view-results";
    }
    
    /**
     * Extract topics from questions for Random Forest analysis
     * This analyzes question text to determine subject areas
     */
    private List<String> extractTopicsFromQuestions(List<String> questions, String defaultSubject) {
        List<String> topics = new ArrayList<>();
        
        // Keywords for different topics
        Map<String, List<String>> topicKeywords = new HashMap<>();
        topicKeywords.put("Security", Arrays.asList("phishing", "malware", "firewall", "encryption", "authentication", "vulnerability", "attack", "threat", "password", "zero-day"));
        topicKeywords.put("Networking", Arrays.asList("router", "switch", "ip", "tcp", "udp", "osi", "protocol", "network", "dns", "dhcp", "subnet", "gateway"));
        topicKeywords.put("Programming", Arrays.asList("python", "java", "code", "function", "variable", "loop", "algorithm", "syntax", "debug", "compile"));
        topicKeywords.put("Operating Systems", Arrays.asList("windows", "linux", "mac", "os", "kernel", "process", "thread", "memory", "file system"));
        topicKeywords.put("Database", Arrays.asList("sql", "database", "query", "table", "index", "primary key", "foreign key", "join", "select"));
        topicKeywords.put("Hardware", Arrays.asList("cpu", "ram", "hard drive", "ssd", "motherboard", "gpu", "memory", "storage", "processor"));
        topicKeywords.put("Cloud", Arrays.asList("cloud", "aws", "azure", "saas", "paas", "iaas", "virtual", "container", "docker"));
        topicKeywords.put("Web Development", Arrays.asList("html", "css", "javascript", "http", "url", "browser", "website", "web", "frontend", "backend"));
        
        for (String question : questions) {
            String questionLower = question.toLowerCase();
            String detectedTopic = defaultSubject != null ? defaultSubject : "General";
            int maxMatches = 0;
            
            // Find topic with most keyword matches
            for (Map.Entry<String, List<String>> entry : topicKeywords.entrySet()) {
                int matches = 0;
                for (String keyword : entry.getValue()) {
                    if (questionLower.contains(keyword)) {
                        matches++;
                    }
                }
                if (matches > maxMatches) {
                    maxMatches = matches;
                    detectedTopic = entry.getKey();
                }
            }
            
            topics.add(detectedTopic);
        }
        
        return topics;
    }
    
    /**
     * Teacher view of student's Random Forest Performance Analytics
     */
    @GetMapping("/performance-analytics/{submissionId}")
    public String viewTeacherPerformanceAnalytics(@PathVariable Long submissionId, Model model) {
        Optional<ExamSubmission> submissionOpt = examSubmissionRepository.findById(submissionId);
        
        if (submissionOpt.isEmpty()) {
            model.addAttribute("error", "Submission not found");
            return "redirect:/teacher/homepage";
        }
        
        ExamSubmission submission = submissionOpt.get();
        
        // Add submission to model
        model.addAttribute("submission", submission);
        
        // Calculate strengths and weaknesses
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        
        if (submission.getTopicMastery() >= 70) strengths.add("Topic Mastery");
        else weaknesses.add("Topic Mastery");
        
        if (submission.getDifficultyResilience() >= 70) strengths.add("Difficulty Resilience");
        else weaknesses.add("Difficulty Resilience");
        
        if (submission.getAccuracy() >= 70) strengths.add("Accuracy");
        else weaknesses.add("Accuracy");
        
        if (submission.getTimeEfficiency() >= 70) strengths.add("Time Efficiency");
        else weaknesses.add("Time Efficiency");
        
        if (submission.getConfidence() >= 70) strengths.add("Confidence");
        else weaknesses.add("Confidence");
        
        model.addAttribute("strengths", strengths);
        model.addAttribute("weaknesses", weaknesses);
        
        // Generate personalized recommendations
        List<String> recommendations = generateTeacherRecommendations(submission);
        model.addAttribute("recommendations", recommendations);
        
        // Calculate overall performance
        double overallScore = (submission.getTopicMastery() + 
                               submission.getDifficultyResilience() + 
                               submission.getAccuracy() + 
                               submission.getTimeEfficiency() + 
                               submission.getConfidence()) / 5.0;
        model.addAttribute("overallScore", String.format("%.2f", overallScore));
        
        // Determine performance message
        String performanceMessage;
        if (overallScore >= 80) {
            performanceMessage = "Student demonstrates excellent performance across all dimensions.";
        } else if (overallScore >= 60) {
            performanceMessage = "Student shows good understanding but needs improvement in weak areas.";
        } else {
            performanceMessage = "Student requires additional support and intervention.";
        }
        model.addAttribute("performanceMessage", performanceMessage);
        
        System.out.println("📊 Teacher viewing analytics for submission: " + submissionId);
        System.out.println("   Student: " + submission.getStudentEmail());
        System.out.println("   Overall Score: " + String.format("%.2f", overallScore));
        System.out.println("   Category: " + submission.getPerformanceCategory());
        
        return "student-performance-analytics";
    }
    
    /**
     * Generate teacher-focused recommendations
     */
    private List<String> generateTeacherRecommendations(ExamSubmission submission) {
        List<String> recommendations = new ArrayList<>();
        
        // Topic Mastery recommendations
        if (submission.getTopicMastery() < 70) {
            recommendations.add("Provide additional learning materials on " + submission.getExamName() + " topics");
            recommendations.add("Schedule one-on-one tutoring sessions to address knowledge gaps");
        }
        
        // Difficulty Resilience recommendations
        if (submission.getDifficultyResilience() < 70) {
            recommendations.add("Assign more challenging practice problems to build resilience");
            recommendations.add("Encourage problem-solving strategies and critical thinking exercises");
        }
        
        // Accuracy recommendations
        if (submission.getAccuracy() < 70) {
            recommendations.add("Review fundamental concepts with the student");
            recommendations.add("Provide guided practice with immediate feedback");
        }
        
        // Time Efficiency recommendations
        if (submission.getTimeEfficiency() < 70) {
            recommendations.add("Teach time management strategies for exam-taking");
            recommendations.add("Provide timed practice sessions to improve pacing");
        }
        
        // Confidence recommendations
        if (submission.getConfidence() < 70) {
            recommendations.add("Encourage student participation and build confidence through positive reinforcement");
            recommendations.add("Assign easier questions first to build momentum");
        }
        
        // Add positive reinforcement if performance is good
        if (submission.getTopicMastery() >= 70 && submission.getAccuracy() >= 70) {
            recommendations.add("Student shows strong foundational knowledge - consider advanced topics");
        }
        
        // If no weaknesses found
        if (recommendations.isEmpty()) {
            recommendations.add("Student demonstrates excellent performance - maintain current approach");
            recommendations.add("Consider peer tutoring opportunities for this high-performing student");
        }
        
        return recommendations;
    }
}

