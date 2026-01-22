package com.exam.Controller;

import com.exam.entity.User;
import com.exam.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }
    
    @GetMapping("/login")
    public String showLoginPage(@RequestParam(required = false) String error,
                               @RequestParam(required = false) String logout,
                               @RequestParam(required = false) String registered,
                               Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid email or password");
        }
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully");
        }
        if (registered != null) {
            model.addAttribute("success", "Registration successful! Please login.");
        }
        return "login";
    }
    
    @GetMapping("/register")
    public String showRegistrationPage() {
        return "register";
    }
    
    @PostMapping("/register")
    public String registerUser(@RequestParam String email,
                              @RequestParam String password,
                              @RequestParam String fullName,
                              @RequestParam String role,
                              Model model) {
        
        String result;
        
        if ("STUDENT".equals(role)) {
            result = userService.registerStudent(email, password, fullName);
        } else if ("TEACHER".equals(role)) {
            result = userService.registerTeacher(email, password, fullName);
        } else {
            model.addAttribute("error", "Invalid role selected");
            return "register";
        }
        
        if (result.startsWith("ERROR")) {
            model.addAttribute("error", result.replace("ERROR: ", ""));
            return "register";
        } else {
            return "redirect:/login?registered=true";
        }
    }
    
    @GetMapping("/dashboard")
    public String dashboard(java.security.Principal principal) {
        // Redirect based on role
        if (principal != null) {
            User user = userService.findByEmail(principal.getName()).orElse(null);
            if (user != null) {
                if (user.getRole() == User.Role.TEACHER) {
                    return "redirect:/teacher/homepage";
                } else {
                    return "redirect:/student/dashboard";
                }
            }
        }
        return "redirect:/login";
    }
}
