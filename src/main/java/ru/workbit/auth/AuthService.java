package ru.workbit.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.dto.auth.ChangePasswordRequest;
import ru.workbit.dto.auth.LoginRequest;
import ru.workbit.dto.auth.RegistrationRequest;
import ru.workbit.dto.auth.TokenResponse;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.security.service.JWTService;
import ru.workbit.user.model.User;
import ru.workbit.user.repository.UserJPARepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserJPARepository userRepository;
    private final JWTService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TokenResponse register(RegistrationRequest request) {
        checkEmail(request.email());
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();
        User saved = userRepository.save(user);
        return new TokenResponse(jwtService.generateToken(saved));
    }

    public TokenResponse login(LoginRequest request) {
        return new TokenResponse(jwtService.generateToken(
                authenticate(request))
        );
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        verifyPassword(request.oldPassword(), user.getPassword());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
    }

//    public void resetPassword() {}

    private void checkEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BadCredentialsException("Email already in use");
        }
    }

    private User authenticate(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        verifyPassword(request.password(), user.getPassword());
        return user;
    }

    private void verifyPassword(String raw, String encoded) {
        if (!passwordEncoder.matches(raw, encoded)) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }
}
