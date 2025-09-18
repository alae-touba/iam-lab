package com.alae.iam.basic_auth_mysql.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alae.iam.basic_auth_mysql.domain.Authority;
import com.alae.iam.basic_auth_mysql.domain.User;
import com.alae.iam.basic_auth_mysql.dto.RegisterRequest;
import com.alae.iam.basic_auth_mysql.dto.RegisterResponse;
import com.alae.iam.basic_auth_mysql.repository.AuthorityRepository;
import com.alae.iam.basic_auth_mysql.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already taken");
        }

        Authority roleUser = authorityRepository.findByName("ROLE_USER")
                .orElseGet(() -> authorityRepository.save(Authority.builder().name("ROLE_USER").build()));

        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .enabled(true)
                .build();
        user.getAuthorities().add(roleUser);

        User saved = userRepository.save(user);
        return new RegisterResponse(saved.getId(), saved.getUsername(), saved.getEmail());
    }
}
