package io.github.yusufakcay_dev.user_service.service;

import io.github.yusufakcay_dev.user_service.dto.AuthRequest;
import io.github.yusufakcay_dev.user_service.dto.LoginRequest;
import io.github.yusufakcay_dev.user_service.entity.User;
import io.github.yusufakcay_dev.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public String register(AuthRequest request) {
        // Default to CUSTOMER if no role provided
        User.Role role = (request.getRole() == null) ? User.Role.CUSTOMER : User.Role.valueOf(request.getRole());

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword())) // Hash it!
                .role(role)
                .build();

        repository.save(user);
        return jwtService.generateToken(user);
    }

    public String login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());

        User user = repository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("Failed login: user not found - {}", request.getUsername());
                    return new RuntimeException("Invalid username or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Failed login: wrong password - {}", request.getUsername());
            throw new RuntimeException("Invalid username or password");
        }

        log.info("Successful login: {}", request.getUsername());
        return jwtService.generateToken(user);
    }
}