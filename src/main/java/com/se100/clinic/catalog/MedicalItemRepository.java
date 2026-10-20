package com.se100.clinic.catalog;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface MedicalItemRepository extends JpaRepository<MedicalItem, Long> {
  boolean existsByName(String name);

  List<MedicalItem> findAllByNameIn(Collection<String> names);
}
