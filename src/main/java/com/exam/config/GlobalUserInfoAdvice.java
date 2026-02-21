package com.exam.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.exam.entity.User;
import com.exam.repository.UserRepository;

/**
 * Exposes authenticated user's information (full name, email) to all Thymeleaf views.
 */
@ControllerAdvice
@Component
public class GlobalUserInfoAdvice {

    @Autowired
    private UserRepository userRepository;

    /**
     * Make the current user's full name available as "currentUserFullName" in all models.
     */
    @ModelAttribute("currentUserFullName")
    public String currentUserFullName(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(User::getFullName)
                .orElse(email);
    }

    /**
     * Optionally expose the current user's email as well.
     */
    @ModelAttribute("currentUserEmail")
    public String currentUserEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }
}
