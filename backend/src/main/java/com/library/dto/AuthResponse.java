package com.library.dto;

import com.library.entity.Role;

public record AuthResponse(
        String token,
        String email,
        String fullName,
        Role role
) {
}
