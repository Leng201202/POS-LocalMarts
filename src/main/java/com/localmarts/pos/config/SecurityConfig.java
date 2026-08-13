package com.localmarts.pos.config;

import com.localmarts.pos.scanner.ScannerSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ScannerSessionService scannerSessionService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/css/**", "/js/**", "/images/**").permitAll()
                        // Phone scanner routes: the phone never logs in as ADMIN/CASHIER.
                        // The scanner token in the URL is what authorizes these calls.
                        .requestMatchers("/scanner/**", "/api/scanner/**").permitAll()
                        .requestMatchers("/pos/scanner", "/pos/scanner/**").hasAnyRole("ADMIN", "CASHIER")
                        .requestMatchers(
                                "/admin", "/admin/**",
                                "/users", "/users/**",
                                "/products", "/products/**",
                                "/unit", "/unit/**",
                                "/category", "/category/**",
                                "/inventory", "/inventory/**",
                                "/audit", "/audit/**",
                                "/loyalty-config", "/loyalty-config/**"
                        ).hasRole("ADMIN")
                        .requestMatchers("/cashier", "/cashier/**").hasAnyRole("ADMIN", "CASHIER")
                        .anyRequest().authenticated()
                )
                // The phone holds no cashier session/cookie, so it can never carry a
                // Spring Security CSRF token. Authorization for these endpoints comes
                // from the scanner token itself, not from session/cookie state, so
                // exempting just this API from CSRF does not weaken the authenticated
                // POS routes (which keep CSRF protection).
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/scanner/**"))
                .formLogin(form -> form
                        .loginPage("/")
                        .loginProcessingUrl("/login")
                        .successHandler((request, response, authentication) -> {
                            boolean admin = authentication.getAuthorities().stream()
                                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

                            if (admin) {
                                response.sendRedirect("/admin");
                            } else {
                                response.sendRedirect("/cashier");
                            }
                        })
                        .failureUrl("/?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout")
                        // A cashier logging out means the till is no longer staffed,
                        // so any paired phone scanner should be dropped with it.
                        .addLogoutHandler((request, response, authentication) -> scannerSessionService.disconnectAll())
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
