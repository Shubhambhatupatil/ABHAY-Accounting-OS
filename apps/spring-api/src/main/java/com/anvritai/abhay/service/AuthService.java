package com.anvritai.abhay.service;

import com.anvritai.abhay.api.AuthDtos.AuthResponse;
import com.anvritai.abhay.api.AuthDtos.LoginRequest;
import com.anvritai.abhay.api.AuthDtos.SignupRequest;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.repository.UserRepository;
import com.anvritai.abhay.security.JwtService;
import com.anvritai.abhay.security.UserPrincipal;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account already exists for this email.");
        }
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(request.name().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        user = users.save(user);
        return response(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("Account not found."));
        return response(user);
    }

    private AuthResponse response(User user) {
        UserPrincipal principal = new UserPrincipal(
                user.getId(), user.getEmail(), user.getPasswordHash(), user.isActive());
        return new AuthResponse(jwtService.issue(principal), "Bearer", user.getId(), user.getEmail(), user.getDisplayName());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
