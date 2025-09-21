package com.alae.iam.session_auth_mysql.service;

import com.alae.iam.session_auth_mysql.domain.User;
import com.alae.iam.session_auth_mysql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userNameOrEmail) throws UsernameNotFoundException {
        User user = userRepository
            .findByUsernameOrEmail(userNameOrEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with username or email: " + userNameOrEmail));
        return user;
    }
}