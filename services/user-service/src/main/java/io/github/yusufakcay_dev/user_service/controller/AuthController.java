package io.github.yusufakcay_dev.user_service.controller;

import io.github.yusufakcay_dev.user_service.dto.AuthRequest;
import io.github.yusufakcay_dev.user_service.dto.AuthResponse;
import io.github.yusufakcay_dev.user_service.dto.LoginRequest;
import io.github.yusufakcay_dev.user_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@RequestBody @Valid AuthRequest request) {
        return new AuthResponse(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody @Valid LoginRequest request) {
        return new AuthResponse(authService.login(request));
    }

}