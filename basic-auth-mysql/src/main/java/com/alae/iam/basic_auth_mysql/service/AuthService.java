package com.alae.iam.basic_auth_mysql.service;

import com.alae.iam.basic_auth_mysql.domain.Authority;
import com.alae.iam.basic_auth_mysql.domain.User;
import com.alae.iam.basic_auth_mysql.dto.RegisterRequest;
import com.alae.iam.basic_auth_mysql.dto.RegisterResponse;
import com.alae.iam.basic_auth_mysql.exception.UserAlreadyExistsException;
import com.alae.iam.basic_auth_mysql.repository.AuthorityRepository;
import com.alae.iam.basic_auth_mysql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        if (userRepository.existsByUsernameOrEmail(req.username(), req.email())) {
            throw new UserAlreadyExistsException("Username or email already taken");
        }

        Authority roleUser = authorityRepository.findByName("ROLE_USER")
                .orElseGet(() -> authorityRepository.save(Authority.builder().name("ROLE_USER").build()));

        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .enabled(true)
                .build();
        user.getRoles().add(roleUser);

        User saved = userRepository.save(user);
        return new RegisterResponse(saved.getId(), saved.getUsername(), saved.getEmail());
    }
}
