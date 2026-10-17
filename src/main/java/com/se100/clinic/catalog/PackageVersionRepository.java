package com.se100.clinic.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PackageVersionRepository extends JpaRepository<PackageVersion, Long> {
  Optional<PackageVersion> findByCode(String code);
}
