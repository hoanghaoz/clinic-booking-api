package com.se100.clinic.catalog;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface MedicalItemRepository
    extends JpaRepository<MedicalItem, Long>, JpaSpecificationExecutor<MedicalItem> {
  boolean existsByName(String name);

  List<MedicalItem> findAllByNameIn(Collection<String> names);
}
