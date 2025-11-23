package com.alae.iam.basic_auth_mysql.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alae.iam.basic_auth_mysql.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByUsername(String username);
  boolean existsByUsername(String username);
  boolean existsByEmail(String email);
  boolean existsByUsernameOrEmail(String username, String email);
}
