package com.exam.algo.SecurityConfig;package com.exam.algo.SecurityConfig;



// DISABLED - Using com.exam.config.SecurityConfig instead// DISABLED - Using com.exam.config.SecurityConfig instead

// This old configuration was conflicting with the new authentication system// This old configuration was conflicting with the new authentication system

// with custom login page and MySQL database authentication

/*

/**import org.springframework.context.annotation.Bean;

 * This class has been disabled because it was using in-memory authenticationimport org.springframework.context.annotation.Configuration;

 * and causing conflicts with the new database-backed authentication system.import org.springframework.security.config.annotation.web.builders.HttpSecurity;

 * import org.springframework.security.core.userdetails.User;

 * The active security configuration is now in:import org.springframework.security.core.userdetails.UserDetails;

 * com.exam.config.SecurityConfigimport org.springframework.security.provisioning.InMemoryUserDetailsManager;

 * import org.springframework.security.web.SecurityFilterChain;

 * DO NOT re-enable this @Configuration annotation without removing the other SecurityConfig

 */@Configuration

public class WebSecurityConfig {

// @Configuration - COMMENTED OUT TO DISABLE THIS CONFIG

public class WebSecurityConfig {    @Bean

        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    // Old in-memory authentication - NO LONGER USED        http

    // Using CustomUserDetailsService with MySQL database instead            .authorizeHttpRequests((requests) -> requests

}                .requestMatchers("/homepage/**", "/process-exams/**", "/enroll-student/**", "/distribute/**").hasRole("TEACHER")

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