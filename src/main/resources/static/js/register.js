// Register Page JavaScript
document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('registerForm');
    const email = document.getElementById('email');
    const password = document.getElementById('password');
    const confirmPassword = document.getElementById('confirmPassword');
    const roleStudent = document.getElementById('roleStudent');
    const roleTeacher = document.getElementById('roleTeacher');
    const emailHelp = document.getElementById('emailHelp');
    const passwordHelp = document.getElementById('passwordHelp');
    
    // Email validation based on role
    roleStudent.addEventListener('change', function() {
        if (this.checked) {
            emailHelp.classList.remove('d-none');
            email.placeholder = 'your.name@student.school.edu';
        }
    });
    
    roleTeacher.addEventListener('change', function() {
        if (this.checked) {
            emailHelp.classList.add('d-none');
            email.placeholder = 'your.email@school.edu';
        }
    });
    
    // Password confirmation validation
    confirmPassword.addEventListener('input', function() {
        if (password.value !== confirmPassword.value) {
            passwordHelp.classList.remove('d-none');
            confirmPassword.setCustomValidity('Passwords do not match');
        } else {
            passwordHelp.classList.add('d-none');
            confirmPassword.setCustomValidity('');
        }
    });
    
    password.addEventListener('input', function() {
        if (confirmPassword.value !== '') {
            if (password.value !== confirmPassword.value) {
                passwordHelp.classList.remove('d-none');
                confirmPassword.setCustomValidity('Passwords do not match');
            } else {
                passwordHelp.classList.add('d-none');
                confirmPassword.setCustomValidity('');
            }
        }
    });
    
    // Form validation
    form.addEventListener('submit', function(e) {
        // Check password match
        if (password.value !== confirmPassword.value) {
            e.preventDefault();
            passwordHelp.classList.remove('d-none');
            confirmPassword.focus();
            return false;
        }
        
        // Validate student email
        if (roleStudent.checked) {
            const schoolEmailPattern = /@(student\.school\.edu|school\.edu)$/;
            if (!schoolEmailPattern.test(email.value)) {
                e.preventDefault();
                alert('⚠️ Students must use a school email address (@student.school.edu)');
                email.focus();
                return false;
            }
        }
        
        // Validate password length
        if (password.value.length < 6) {
            e.preventDefault();
            alert('⚠️ Password must be at least 6 characters long');
            password.focus();
            return false;
        }
        
        return true;
    });
    
    // Show password strength indicator
    password.addEventListener('input', function() {
        const strength = calculatePasswordStrength(password.value);
        // You can add a visual indicator here if needed
    });
    
    function calculatePasswordStrength(pwd) {
        let strength = 0;
        if (pwd.length >= 6) strength++;
        if (pwd.length >= 10) strength++;
        if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) strength++;
        if (/\d/.test(pwd)) strength++;
        if (/[^a-zA-Z0-9]/.test(pwd)) strength++;
        return strength;
    }
});
