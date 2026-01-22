package com.exam.service;

import com.exam.entity.User;
import com.exam.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    
    // School email domain pattern (customize for your school)
    private static final Pattern SCHOOL_EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9._%+-]+@(student\\.school\\.edu|school\\.edu)$");
    
    /**
     * Register a new student (must use school email)
     */
    public String registerStudent(String email, String password, String fullName) {
        // Validate school email
        if (!isValidSchoolEmail(email)) {
            return "ERROR: Please use your school email address (@student.school.edu)";
        }
        
        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            return "ERROR: Email already registered";
        }
        
        // Create new student user
        User student = new User();
        student.setEmail(email);
        student.setPassword(passwordEncoder.encode(password));
        student.setFullName(fullName);
        student.setRole(User.Role.STUDENT);
        student.setEnabled(true); // Auto-enable or set false for email verification
        student.setVerificationToken(UUID.randomUUID().toString());
        
        userRepository.save(student);
        
        return "SUCCESS: Student registered successfully!";
    }
    
    /**
     * Register a new teacher (any valid email)
     */
    public String registerTeacher(String email, String password, String fullName) {
        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            return "ERROR: Email already registered";
        }
        
        // Create new teacher user
        User teacher = new User();
        teacher.setEmail(email);
        teacher.setPassword(passwordEncoder.encode(password));
        teacher.setFullName(fullName);
        teacher.setRole(User.Role.TEACHER);
        teacher.setEnabled(true);
        
        userRepository.save(teacher);
        
        return "SUCCESS: Teacher registered successfully!";
    }
    
    /**
     * Validate school email format
     */
    public boolean isValidSchoolEmail(String email) {
        return SCHOOL_EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Find user by email
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    /**
     * Authenticate user
     */
    public boolean authenticate(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            return passwordEncoder.matches(password, user.getPassword()) && user.isEnabled();
        }
        return false;
    }
}
