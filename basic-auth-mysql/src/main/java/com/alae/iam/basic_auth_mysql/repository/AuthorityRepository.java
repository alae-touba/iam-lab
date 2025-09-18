package com.alae.iam.basic_auth_mysql.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alae.iam.basic_auth_mysql.domain.Authority;

public interface AuthorityRepository extends JpaRepository<Authority, Long> {
  Optional<Authority> findByName(String name);
}
