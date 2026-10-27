package com.se100.clinic.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PackageVersionItemRepository
    extends JpaRepository<PackageVersionItem, PackageVersionItemId> {
  List<PackageVersionItem> findAllByPackageVersionIdOrderByPositionAsc(Long packageVersionId);
}
