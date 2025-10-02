package com.alae.iam.session_auth_mysql.dto;

import com.alae.iam.session_auth_mysql.domain.User;

import java.util.List;
import java.util.stream.Collectors;

public record UserSummary(Long id, String username, String email, List<String> authorities) {

    public static UserSummary from(User user) {
        List<String> authorities = user.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .collect(Collectors.toList());

        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                authorities
        );
    }
}
