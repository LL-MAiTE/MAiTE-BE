package com.likelion.hackathon.domain.user.controller;

import com.likelion.hackathon.domain.user.dto.UserResponse;
import com.likelion.hackathon.domain.user.service.UserService;
import com.likelion.hackathon.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/users")
    public ApiResponse<UserResponse> searchByEmail(@RequestParam String email) {
        return ApiResponse.ok(userService.searchByEmail(email));
    }
}
