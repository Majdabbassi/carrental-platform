package com.back.car_rent.controller;

import com.back.car_rent.dto.*;
import com.back.car_rent.model.User;
import com.back.car_rent.repository.UserRepository;
import com.back.car_rent.security.JwtService;
import com.back.car_rent.security.Role;
import com.back.car_rent.service.MailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final com.back.car_rent.security.PermissionService perm;

    @org.springframework.beans.factory.annotation.Value("${app.web-url:http://localhost:4200}")
    private String webUrl;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          MailService mailService,
                          com.back.car_rent.security.PermissionService perm) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.perm = perm;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid AuthRequest req) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        User user = userRepository.findByUsername(req.getUsername()).orElseThrow();
        String access = jwtService.generateToken(user.getUsername(), user.getRole(), null);
        String refresh = jwtService.generateRefreshToken(user.getUsername(), user.getRole());
        return ResponseEntity.ok(new AuthResponse(access, refresh, user.getUsername(), user.getRole()));
    }

    /** Who am I, and which sections of the back office may I use (drives the menu of the web app). */
    @GetMapping("/me")
    public ResponseEntity<java.util.Map<String, Object>> me(org.springframework.security.core.Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("username", user.getUsername());
        body.put("email", user.getEmail());
        body.put("role", user.getRole());
        java.util.Map<String, Boolean> sections = new java.util.LinkedHashMap<>();
        for (String s : java.util.List.of("dashboard", "cars", "clients", "contracts", "payments", "partners", "expenses", "reports")) {
            sections.put(s, perm.can(s));
        }
        sections.put("employees", perm.isAdmin());
        body.put("sections", sections);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequest req) {
        Optional<User> userOpt = req.getUsernameOrEmail().contains("@")
                ? userRepository.findByEmail(req.getUsernameOrEmail())
                : userRepository.findByUsername(req.getUsernameOrEmail());
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok().build(); // do not reveal existence
        }
        User user = userOpt.get();
        // Use current password hash to ensure token is invalidated if password already changed
        String resetToken = jwtService.generatePasswordResetToken(user.getUsername(), user.getPassword());
        String resetLink = webUrl + "/reset-password?token=" + resetToken;
        String to = user.getEmail() != null ? user.getEmail() : user.getUsername();
        mailService.send(to, "Reset your password", "Click the link to reset your password: " + resetLink);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest req) {
        String username = jwtService.extractUsername(req.getToken());
        if (username == null) return ResponseEntity.badRequest().build();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().build();
        User user = userOpt.get();
        boolean valid = jwtService.isPasswordResetTokenValid(req.getToken(), user.getUsername(), user.getPassword());
        if (!valid) return ResponseEntity.badRequest().build();
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody @Valid RefreshTokenRequest req) {
        String username = jwtService.extractUsername(req.getRefreshToken());
        if (username == null) return ResponseEntity.status(401).build();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();
        User user = userOpt.get();
        if (!jwtService.isRefreshTokenValid(req.getRefreshToken(), user.getUsername())) {
            return ResponseEntity.status(401).build();
        }
        String access = jwtService.generateToken(user.getUsername(), user.getRole(), null);
        String newRefresh = jwtService.generateRefreshToken(user.getUsername(), user.getRole()); // rotate
        return ResponseEntity.ok(new AuthResponse(access, newRefresh, user.getUsername(), user.getRole()));
    }
}