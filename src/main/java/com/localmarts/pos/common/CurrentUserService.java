package com.localmarts.pos.common;

import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("No authenticated user found");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Authenticated user not found: "
                                        + authentication.getName()
                        )
                );
    }

    public Long getCurrentUserId(Authentication authentication) {
        return getCurrentUser(authentication).getId();
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                );
    }
}