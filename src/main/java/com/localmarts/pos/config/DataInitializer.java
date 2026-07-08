package com.localmarts.pos.config;

import com.localmarts.pos.user.Role;
import com.localmarts.pos.user.User;
import com.localmarts.pos.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        createDefaultUser("System Administrator", "admin", "admin123", Role.ADMIN);
        createDefaultUser("Default Cashier", "cashier", "cashier123", Role.CASHIER);
    }

    private void createDefaultUser(String fullName, String username, String password, Role role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }

        User user = User.builder()
                .fullName(fullName)
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .build();

        userRepository.save(user);
    }
}
