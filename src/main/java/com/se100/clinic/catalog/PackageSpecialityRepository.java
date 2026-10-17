package com.se100.clinic.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PackageSpecialityRepository
    extends JpaRepository<PackageSpeciality, PackageSpecialityId> {
  List<PackageSpeciality> findAllByMedicalPackage(MedicalPackage medicalPackage);
}
