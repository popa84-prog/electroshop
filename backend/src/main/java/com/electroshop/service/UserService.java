package com.electroshop.service;

import com.electroshop.dto.LoginEventDto;
import com.electroshop.dto.UserDto;
import com.electroshop.dto.UserRequest;
import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.Role;
import com.electroshop.model.RoleName;
import com.electroshop.model.User;
import com.electroshop.repository.LoginEventRepository;
import com.electroshop.repository.RoleRepository;
import com.electroshop.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginEventRepository loginEventRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, LoginEventRepository loginEventRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginEventRepository = loginEventRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserDto> list(String search, Pageable pageable) {
        Page<User> page = (search == null || search.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        search, search, pageable);
        return page.map(UserDto::from);
    }

    /** Accounts awaiting admin approval. */
    @Transactional(readOnly = true)
    public Page<UserDto> listPending(Pageable pageable) {
        return userRepository.findPending(pageable).map(UserDto::from);
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return userRepository.countPending();
    }

    /** Approve a pending account so its owner can log in. */
    public UserDto approve(Long id) {
        User user = findEntity(id);
        user.setApproved(true);
        return UserDto.from(userRepository.save(user));
    }

    /** Connection history (all users, or a single user when userId is provided). */
    @Transactional(readOnly = true)
    public Page<LoginEventDto> listLoginEvents(Long userId, Pageable pageable) {
        return (userId == null
                ? loginEventRepository.findAllByOrderByLoginAtDesc(pageable)
                : loginEventRepository.findByUserIdOrderByLoginAtDesc(userId, pageable))
                .map(LoginEventDto::from);
    }

    @Transactional(readOnly = true)
    public UserDto getById(Long id) {
        return UserDto.from(findEntity(id));
    }

    @Transactional(readOnly = true)
    public UserDto getByEmail(String email) {
        return UserDto.from(userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email)));
    }

    public UserDto create(UserRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BadRequestException("Email is already in use");
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new BadRequestException("Password is required when creating a user");
        }
        User user = new User();
        user.setFullName(req.fullName());
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setEnabled(req.enabled() == null || req.enabled());
        user.setApproved(true); // accounts created by an admin are approved immediately
        user.setRoles(resolveRoles(req.roles()));
        return UserDto.from(userRepository.save(user));
    }

    public UserDto update(Long id, UserRequest req) {
        User user = findEntity(id);

        if (!user.getEmail().equals(req.email()) && userRepository.existsByEmail(req.email())) {
            throw new BadRequestException("Email is already in use");
        }
        user.setFullName(req.fullName());
        user.setEmail(req.email());
        if (req.password() != null && !req.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.password()));
        }
        if (req.enabled() != null) {
            user.setEnabled(req.enabled());
        }
        if (req.roles() != null && !req.roles().isEmpty()) {
            user.setRoles(resolveRoles(req.roles()));
        }
        return UserDto.from(userRepository.save(user));
    }

    public void delete(Long id) {
        User user = findEntity(id);
        userRepository.delete(user);
    }

    public User findEntity(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        if (roleNames == null || roleNames.isEmpty()) {
            roles.add(getRole(RoleName.ROLE_USER));
            return roles;
        }
        for (String name : roleNames) {
            RoleName rn;
            try {
                rn = RoleName.valueOf(name.startsWith("ROLE_") ? name : "ROLE_" + name);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown role: " + name);
            }
            roles.add(getRole(rn));
        }
        return roles;
    }

    private Role getRole(RoleName rn) {
        return roleRepository.findByName(rn)
                .orElseGet(() -> roleRepository.save(new Role(rn)));
    }
}
