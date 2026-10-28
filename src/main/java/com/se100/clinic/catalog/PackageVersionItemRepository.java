package com.se100.clinic.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PackageVersionItemRepository
    extends JpaRepository<PackageVersionItem, PackageVersionItemId> {
  // The item is fetched with the rows: responses read its public id for every row.
  // solves problem of N+1 queries when fetching package version items with their items.
  @EntityGraph(attributePaths = {"item"})
  List<PackageVersionItem> findAllByPackageVersionIdOrderByPositionAsc(Long packageVersionId);
}
