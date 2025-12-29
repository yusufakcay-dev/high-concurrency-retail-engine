package io.github.yusufakcay_dev.user_service.controller;

import io.github.yusufakcay_dev.user_service.dto.UserResponse;
import io.github.yusufakcay_dev.user_service.entity.User;
import io.github.yusufakcay_dev.user_service.repository.UserRepository;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository repository;

    @GetMapping("/me")
    public UserResponse getCurrentUser(
            @Parameter(hidden = true) @RequestHeader("X-User-Name") String username) {
        User user = repository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }
}