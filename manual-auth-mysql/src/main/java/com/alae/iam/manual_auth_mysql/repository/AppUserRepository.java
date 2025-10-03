package com.alae.iam.manual_auth_mysql.repository;

import com.alae.iam.manual_auth_mysql.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByUsername(String username);
  Optional<AppUser> findByEmail(String email);
}
