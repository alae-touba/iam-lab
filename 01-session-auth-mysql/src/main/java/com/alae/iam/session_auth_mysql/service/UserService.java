package com.alae.iam.session_auth_mysql.service;

import com.alae.iam.session_auth_mysql.domain.Authority;
import com.alae.iam.session_auth_mysql.domain.User;
import com.alae.iam.session_auth_mysql.dto.RegisterRequest;
import com.alae.iam.session_auth_mysql.exception.UserAlreadyExistsException;
import com.alae.iam.session_auth_mysql.repository.AuthorityRepository;
import com.alae.iam.session_auth_mysql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User registerNewUser(RegisterRequest registerRequest) {
        if (userRepository.findByUsernameOrEmail(registerRequest.username()).isPresent() || userRepository.findByUsernameOrEmail(registerRequest.email()).isPresent()) {
            throw new UserAlreadyExistsException("User with this username or email already exists");
        }

        User user = new User();
        user.setUsername(registerRequest.username());
        user.setEmail(registerRequest.email());
        user.setPassword(passwordEncoder.encode(registerRequest.password()));

        Authority userAuthority = authorityRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
        user.setAuthorities(Collections.singletonList(userAuthority));

        return userRepository.save(user);
    }
}
