package com.alae.iam.session_auth_mysql.controller;

import com.alae.iam.session_auth_mysql.domain.User;
import com.alae.iam.session_auth_mysql.dto.RegisterRequest;
import com.alae.iam.session_auth_mysql.dto.UserSummary;
import com.alae.iam.session_auth_mysql.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserSummary> registerUser(@RequestBody RegisterRequest registerRequest) {
        User user = userService.registerNewUser(registerRequest);
        return new ResponseEntity<>(UserSummary.from(user), HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public UserSummary getCurrentUser(@AuthenticationPrincipal User user) {
        return UserSummary.from(user);
    }
}
