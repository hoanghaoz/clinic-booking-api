package com.se100.clinic.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface MedicalItemRepository extends JpaRepository<MedicalItem, Long> {
  Optional<MedicalItem> findByName(String name);
}
