package com.se100.clinic.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<User, Long> {

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByUsernameIgnoreCase(String username);

  Optional<User> findByPublicId(UUID publicId);

  @Query(
      "select u from User u where u.username = :identifier or lower(u.email) = lower(:identifier)")
  List<User> findByUsernameOrEmail(@Param("identifier") String identifier);
}
