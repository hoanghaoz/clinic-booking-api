package com.se100.clinic.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackageVersionItemRepository extends JpaRepository<PackageVersionItem, Long> {
  List<PackageVersionItem> findAllByPackageVersionIdInOrderByPositionAsc(Long id);
}
