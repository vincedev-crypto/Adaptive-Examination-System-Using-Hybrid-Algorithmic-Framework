package com.exam.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    
    @Value("${app.base.url:http://localhost:8080}")
    private String baseUrl;
    
    /**
     * Send verification email to user
     */
    public void sendVerificationEmail(String toEmail, String fullName, String verificationToken) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Verify Your Account - Adaptive Examination System");
            
            String verificationUrl = baseUrl + "/verify?token=" + verificationToken;
            
            String emailBody = "Dear " + fullName + ",\n\n"
                    + "Thank you for registering with the Adaptive Examination System!\n\n"
                    + "Please click the link below to verify your email address:\n\n"
                    + verificationUrl + "\n\n"
                    + "This link will expire in 24 hours.\n\n"
                    + "If you did not create an account, please ignore this email.\n\n"
                    + "Best regards,\n"
                    + "Adaptive Examination System Team";
            
            message.setText(emailBody);
            
            mailSender.send(message);
            
            System.out.println("Verification email sent to: " + toEmail);
            
        } catch (Exception e) {
            System.err.println("Error sending email to " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Send password reset email
     */
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetToken) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Password Reset Request - Adaptive Examination System");
            
            String resetUrl = baseUrl + "/reset-password?token=" + resetToken;
            
            String emailBody = "Dear " + fullName + ",\n\n"
                    + "We received a request to reset your password.\n\n"
                    + "Click the link below to reset your password:\n\n"
                    + resetUrl + "\n\n"
                    + "This link will expire in 1 hour.\n\n"
                    + "If you did not request a password reset, please ignore this email.\n\n"
                    + "Best regards,\n"
                    + "Adaptive Examination System Team";
            
            message.setText(emailBody);
            
            mailSender.send(message);
            
            System.out.println("Password reset email sent to: " + toEmail);
            
        } catch (Exception e) {
            System.err.println("Error sending password reset email: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
