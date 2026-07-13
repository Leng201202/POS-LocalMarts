package com.localmarts.pos.user;

import com.localmarts.pos.user.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> findAll() {
    return userRepository.findAll();
}
    public List<User> findAllActive() {
        return userRepository.findByActiveTrueOrderByFullNameAsc();
    }

    public List<User> findAllInactive() {
    return userRepository.findByActiveFalseOrderByFullNameAsc();
}

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public void save(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setActive(true);
        user.setCreatedBy(0L);
        userRepository.save(user);
    }

    public void update(Long id, User formUser) {
    User existingUser = findById(id);

    existingUser.setFullName(formUser.getFullName());
    existingUser.setUsername(formUser.getUsername());
    existingUser.setRole(formUser.getRole());

    userRepository.save(existingUser);
}

    public User findById(Long id) {
    return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

        public void activate(Long id) {
        User user = findById(id);
        user.setActive(true);
        userRepository.save(user);
    }


    public void deactivate(Long id, String currentUsername) {
    User targetUser = findById(id);

    if (targetUser.getUsername().equals(currentUsername)) {
        throw new IllegalStateException(
                "You cannot deactivate your own account."
        );
    }

    targetUser.setActive(false);
    userRepository.save(targetUser);
}
}
