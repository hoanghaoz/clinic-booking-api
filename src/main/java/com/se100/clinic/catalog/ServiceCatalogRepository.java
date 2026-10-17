package com.se100.clinic.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

interface ServiceCatalogRepository extends JpaRepository<MedicalService, Long> {}
