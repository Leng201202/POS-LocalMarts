package com.localmarts.pos.user;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.audit.utils.AuditChangeHelper;
import com.localmarts.pos.user.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                    PasswordEncoder passwordEncoder,
                    AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    private Long getActorId(String actorUsername) {
        return userRepository.findByUsername(actorUsername)
                .orElseThrow(() -> new RuntimeException("Logged-in user not found"))
                .getId();
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

    public void save(User user, String actorUsername) {
        Long actorId = getActorId(actorUsername);

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setActive(true);
        user.setCreatedBy(actorId);

        User savedUser = userRepository.save(user);

        auditService.logSuccess(
                actorId,
                AuditAction.CREATE,
                "User",
                savedUser.getId(),
                "Created user account: " + savedUser.getUsername()
        );
    }

    public void update(Long id, User formUser, String actorUsername) {
        Long actorId = getActorId(actorUsername);
        User existingUser = findById(id);

        List<AuditDetails> details = new AuditChangeHelper()
                .addChange(
                        "fullName",
                        existingUser.getFullName(),
                        formUser.getFullName()
                )
                .addChange(
                        "username",
                        existingUser.getUsername(),
                        formUser.getUsername()
                )
                .addChange(
                        "role",
                        existingUser.getRole(),
                        formUser.getRole()
                )
                .build();

        existingUser.setFullName(formUser.getFullName());
        existingUser.setUsername(formUser.getUsername());
        existingUser.setRole(formUser.getRole());
        existingUser.setUpdatedBy(actorId);

        userRepository.save(existingUser);

        auditService.logSuccess(
                actorId,
                AuditAction.UPDATE,
                "User",
                existingUser.getId(),
                "Updated user account: " + existingUser.getUsername(),
                details
        );
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void activate(Long id, String actorUsername) {
        Long actorId = getActorId(actorUsername);
        User user = findById(id);

        List<AuditDetails> details = new AuditChangeHelper()
                .addChange("active", user.getActive(), true)
                .build();

        user.setActive(true);
        user.setUpdatedBy(actorId);

        userRepository.save(user);

        auditService.logSuccess(
                actorId,
                AuditAction.UPDATE,
                "User",
                user.getId(),
                "Activated user account: " + user.getUsername(),
                details
        );
    }


    public void deactivate(Long id, String currentUsername) {
        Long actorId = getActorId(currentUsername);
        User targetUser = findById(id);

        if (targetUser.getUsername().equals(currentUsername)) {
            auditService.logFailed(
                    actorId,
                    AuditAction.UPDATE,
                    "User",
                    targetUser.getId(),
                    "Failed to deactivate own user account"
            );

            throw new IllegalStateException(
                    "You cannot deactivate your own account."
            );
        }

        List<AuditDetails> details = new AuditChangeHelper()
                .addChange("active", targetUser.getActive(), false)
                .build();

        targetUser.setActive(false);
        targetUser.setUpdatedBy(actorId);

        userRepository.save(targetUser);

        auditService.logSuccess(
                actorId,
                AuditAction.UPDATE,
                "User",
                targetUser.getId(),
                "Deactivated user account: " + targetUser.getUsername(),
                details
        );
    }
}