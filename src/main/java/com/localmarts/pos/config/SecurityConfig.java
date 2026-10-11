package com.localmarts.pos.config;

import org.springframework.http.HttpMethod;

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
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers(
                                "/admin", "/admin/**",
                                "/users", "/users/**",
                                "/products", "/products/**",
                                "/unit", "/unit/**",
                                "/category", "/category/**",
                                "/audit", "/audit/**",
                                "/users","/users/**",
                                "/customer", "/customer/**",
                                "/loyalty-config", "/loyalty-config/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/inventory", "/inventory/adjustment", "/inventory/adjustment/products",
                                "/inventory/stock-in/manual")
                        .hasAnyRole("ADMIN", "CASHIER")
                        .requestMatchers(HttpMethod.POST, "/inventory/adjustment", "/inventory/stock-in/manual")
                        .hasAnyRole("ADMIN", "CASHIER")
                        .requestMatchers("/inventory", "/inventory/**").hasRole("ADMIN")
                        .requestMatchers("/cashier/reports", "/cashier/reports/**").hasRole("CASHIER")
                        .requestMatchers(
                                "/pos/scanner", "/pos/scanner/**",
                                "/cashier", "/cashier/**"
                        ).hasAnyRole("ADMIN", "CASHIER")
                        .anyRequest().authenticated()
                )
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
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
