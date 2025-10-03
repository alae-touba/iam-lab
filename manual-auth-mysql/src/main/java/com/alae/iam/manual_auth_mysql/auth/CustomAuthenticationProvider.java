package com.alae.iam.manual_auth_mysql.auth;

import com.alae.iam.manual_auth_mysql.domain.AppUser;
import com.alae.iam.manual_auth_mysql.domain.AuthPrincipal;
import com.alae.iam.manual_auth_mysql.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

  private final AppUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    final String usernameOrEmail = authentication.getName();
    
    if (authentication.getCredentials() == null) {
      throw new BadCredentialsException("Invalid credentials");
    }

    final String rawPassword = authentication.getCredentials().toString();

    AppUser user = userRepository.findByUsername(usernameOrEmail)
        .or(() -> userRepository.findByEmail(usernameOrEmail))
        .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

    if (!user.isEnabled()) {
      throw new DisabledException("Account disabled");
    }

    if (user.isAccountLocked()) {
      throw new LockedException("Account locked");
    }

    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new BadCredentialsException("Invalid credentials");
    }

    List<String> roles = List.of("ROLE_USER");
    var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();
    var principal = new AuthPrincipal(user.getId(), user.getUsername(), user.getEmail());

    // never return the raw password
    return new UsernamePasswordAuthenticationToken(principal, null, authorities);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
