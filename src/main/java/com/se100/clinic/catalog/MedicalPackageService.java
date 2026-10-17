package com.se100.clinic.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MedicalPackageService {
  private final MedicalPackageRepository medicalPackageRepository;

  public String createMedicalPackage(CatalogDtos.MedicalPackageCreateDto dto) {

  }
}
