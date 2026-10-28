package com.se100.clinic.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface MedicalItemRepository
    extends JpaRepository<MedicalItem, Long>, JpaSpecificationExecutor<MedicalItem> {
  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, Long id);

  Optional<MedicalItem> findByPublicId(UUID publicId);

  List<MedicalItem> findAllByNameIn(Collection<String> names);

  List<MedicalItem> findAllByPublicIdIn(Collection<UUID> publicIds);
}
