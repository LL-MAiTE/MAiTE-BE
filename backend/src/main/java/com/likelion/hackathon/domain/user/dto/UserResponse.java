package com.likelion.hackathon.domain.user.dto;

import com.likelion.hackathon.domain.user.entity.User;

import java.util.UUID;

public record UserResponse(UUID id, String email, String name) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName());
    }
}
