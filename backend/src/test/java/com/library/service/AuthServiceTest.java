package com.library.service;

import com.library.config.JwtService;
import com.library.dto.AuthResponse;
import com.library.dto.LoginRequest;
import com.library.dto.RegisterRequest;
import com.library.entity.Role;
import com.library.entity.User;
import com.library.exception.DuplicateResourceException;
import com.library.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_success_encodesPasswordAssignsMemberRoleAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("  New.User@Library.com ", "Secret123!", "  Teszt Elek  ");

        when(userRepository.existsByEmail("new.user@library.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken("new.user@library.com", Role.MEMBER)).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertThat(saved.getEmail()).isEqualTo("new.user@library.com");
        assertThat(saved.getPassword()).isEqualTo("$2a$10$hashed");
        assertThat(saved.getFullName()).isEqualTo("Teszt Elek");
        assertThat(saved.getRole()).isEqualTo(Role.MEMBER);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.email()).isEqualTo("new.user@library.com");
        assertThat(response.fullName()).isEqualTo("Teszt Elek");
        assertThat(response.role()).isEqualTo(Role.MEMBER);
    }

    @Test
    void register_whenEmailAlreadyExists_throwsDuplicateResourceException() {
        RegisterRequest request = new RegisterRequest("member@library.com", "Secret123!", "Kovacs Anna");
        when(userRepository.existsByEmail("member@library.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_success_authenticatesAndReturnsJwt() {
        LoginRequest request = new LoginRequest(" Member@Library.com ", "Member123!");
        User user = new User("member@library.com", "$2a$10$hashed", "Kovacs Anna", Role.MEMBER);

        when(userRepository.findByEmail("member@library.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("member@library.com", Role.MEMBER)).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("member@library.com", "Member123!")
        );
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.role()).isEqualTo(Role.MEMBER);
    }

    @Test
    void login_whenBadCredentials_propagatesBadCredentialsException() {
        LoginRequest request = new LoginRequest("member@library.com", "wrong");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateToken(any(), any());
    }
}
