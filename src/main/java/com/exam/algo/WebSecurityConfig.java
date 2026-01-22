package com.exam.algo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((requests) -> requests
                .requestMatchers("/homepage/**", "/process-exams/**", "/enroll-student/**", "/distribute/**").hasRole("TEACHER")
                .requestMatchers("/student/**").hasRole("STUDENT")
                .anyRequest().authenticated()
            )
            .formLogin((form) -> form
                // Custom logic: Teachers go to /homepage, Students go to /student/dashboard
                .successHandler((request, response, authentication) -> {
                    for (var auth : authentication.getAuthorities()) {
                        if (auth.getAuthority().equals("ROLE_TEACHER")) {
                            response.sendRedirect("/homepage");
                            return;
                        }
                    }
                    response.sendRedirect("/student/dashboard");
                })
                .permitAll()
            )
            .logout((logout) -> logout.permitAll());

        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails teacher = User.withDefaultPasswordEncoder()
            .username("charles")
            .password("123")
            .roles("TEACHER")
            .build();

        // Student ID format: 00-0-00000
        UserDetails student = User.withDefaultPasswordEncoder()
            .username("00-0-00000")
            .password("123")
            .roles("STUDENT")
            .build();

        return new InMemoryUserDetailsManager(teacher, student);
    }
}