package com.library.dto;

import com.library.entity.Role;
import com.library.entity.User;

import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        LocalDateTime createdAt
) {
    public static MemberResponse from(User user) {
        return new MemberResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
