package com.anvritai.abhay.api;

import com.anvritai.abhay.api.AuthDtos.AuthResponse;
import com.anvritai.abhay.api.AuthDtos.LoginRequest;
import com.anvritai.abhay.api.AuthDtos.MeResponse;
import com.anvritai.abhay.api.AuthDtos.SignupRequest;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.repository.UserRepository;
import com.anvritai.abhay.security.UserPrincipal;
import com.anvritai.abhay.service.AuthService;
import com.anvritai.abhay.service.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = users.findById(principal.id()).orElseThrow(() -> new NotFoundException("Account not found."));
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getSelectedCompany() == null ? null : user.getSelectedCompany().getId());
    }
}
