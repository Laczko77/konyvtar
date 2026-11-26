package com.library.config;

import com.library.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-key-at-least-256-bits-long-123456";

    @Test
    void generateAndValidateToken_succeedsForMatchingUser() {
        JwtService jwtService = new JwtService(SECRET, 60_000L);
        UserDetails userDetails = User.withUsername("member@library.com")
                .password("encoded")
                .roles("MEMBER")
                .build();

        String token = jwtService.generateToken("member@library.com", Role.MEMBER);

        assertThat(jwtService.extractEmail(token)).isEqualTo("member@library.com");
        assertThat(jwtService.extractRole(token)).isEqualTo("MEMBER");
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForDifferentUserOrTamperedOrExpiredToken() {
        JwtService jwtService = new JwtService(SECRET, 60_000L);
        String token = jwtService.generateToken("member@library.com", Role.MEMBER);

        UserDetails otherUser = User.withUsername("other@library.com")
                .password("encoded")
                .roles("MEMBER")
                .build();
        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
        assertThat(jwtService.isTokenValid(token + "tampered", otherUser)).isFalse();

        JwtService expiredJwtService = new JwtService(SECRET, -1_000L);
        String expiredToken = expiredJwtService.generateToken("member@library.com", Role.MEMBER);
        UserDetails matchingUser = User.withUsername("member@library.com")
                .password("encoded")
                .roles("MEMBER")
                .build();
        assertThat(jwtService.isTokenValid(expiredToken, matchingUser)).isFalse();
    }

    @Test
    void constructor_rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService("short-secret", 60_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
