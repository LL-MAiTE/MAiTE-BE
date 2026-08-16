package com.likelion.hackathon.domain.user.controller;

import com.likelion.hackathon.domain.user.dto.AuthResponse;
import com.likelion.hackathon.domain.user.dto.LoginRequest;
import com.likelion.hackathon.domain.user.dto.SignupRequest;
import com.likelion.hackathon.domain.user.service.AuthService;
import com.likelion.hackathon.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> signup(@RequestBody @Valid SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
