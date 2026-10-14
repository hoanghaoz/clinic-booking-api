package com.se100.clinic.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.ToOne;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Verifies doctor mappings with existing specialties without database access or schema creation.
 */
class DoctorMappingTest {

  @Test
  void bootstrap_shouldMatchDoctorSchema_whenEntitiesAreDiscovered() throws ClassNotFoundException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    var entityNames =
        scanner.findCandidateComponents("com.se100.clinic.doctor").stream()
            .map(definition -> definition.getBeanClassName())
            .toList();
    assertThat(entityNames)
        .containsExactlyInAnyOrder(
            "com.se100.clinic.doctor.Doctor",
            "com.se100.clinic.doctor.DoctorMajor",
            "com.se100.clinic.doctor.Specialty");

    var registry =
        new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", PostgreSQLDialect.class.getName())
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .applySetting("hibernate.hbm2ddl.auto", "none")
            .applySetting(
                "hibernate.connection.provider_class",
                UserSuppliedConnectionProviderImpl.class.getName())
            .applySetting(
                "hibernate.physical_naming_strategy",
                PhysicalNamingStrategySnakeCaseImpl.class.getName())
            .build();
    try {
      var sources = new MetadataSources(registry);
      for (String entityName : entityNames) {
        sources.addAnnotatedClass(Class.forName(entityName));
      }
      var metadata = (MetadataImplementor) sources.buildMetadata();
      try (var sessionFactory = metadata.buildSessionFactory()) {
        assertThat(sessionFactory.getMetamodel().getEntities()).hasSize(3);
        assertDoctorMapping(metadata);
        assertDoctorMajorMapping(metadata);
        assertExistingSpecialtyMapping(metadata);
      }
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  private static void assertDoctorMapping(MetadataImplementor metadata) {
    var doctor = metadata.getEntityBinding("com.se100.clinic.doctor.Doctor");
    Table table = doctor.getTable();

    assertThat(table.getName()).isEqualTo("doctors");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id", "public_id", "user_id", "name", "status", "degree", "created_at", "updated_at");
    assertThat(table.getColumns()).allMatch(column -> !column.isNullable());
    assertThat(column(table, "id").isIdentity()).isTrue();
    assertThat(column(table, "public_id").getSqlType(metadata)).isEqualTo("uuid");
    assertThat(column(table, "name").getLength()).isEqualTo(150L);
    assertThat(column(table, "degree").getLength()).isEqualTo(100L);
    assertThat(table.getColumns().stream().filter(Column::isUnique))
        .extracting(Column::getName)
        .containsExactlyInAnyOrder("public_id", "user_id");
    var status = (BasicValue) doctor.getProperty("status").getValue();
    assertThat(status.getEnumeratedType()).isEqualTo(EnumType.STRING);
    assertThat(status.getType().getReturnedClass().getName())
        .isEqualTo("com.se100.clinic.employee.enums.EmployeeStatus");
    assertThat(column(table, "status").getDefaultValue()).isNull();
    assertThat(table.getForeignKeyCollection()).isEmpty();
  }

  private static void assertDoctorMajorMapping(MetadataImplementor metadata) {
    var link = metadata.getEntityBinding("com.se100.clinic.doctor.DoctorMajor");
    Table table = link.getTable();

    assertThat(table.getName()).isEqualTo("doctor_majors");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder("doctor_id", "specialty_id", "created_at");
    assertThat(table.getColumns()).allMatch(column -> !column.isNullable());
    assertThat(table.getPrimaryKey().getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder("doctor_id", "specialty_id");
    assertThat(column(table, "created_at").getSqlType(metadata))
        .matches("timestamp\\(\\d+\\) with time zone");
    var id = (Component) link.getIdentifier();
    assertThat(id.getProperties())
        .extracting(property -> property.getName())
        .containsExactlyInAnyOrder("doctorId", "specialtyId");
    assertThat(id.getProperty("doctorId").getValue().getColumns())
        .extracting(Column::getName)
        .containsExactly("doctor_id");
    assertThat(id.getProperty("specialtyId").getValue().getColumns())
        .extracting(Column::getName)
        .containsExactly("specialty_id");
    assertLazyAssociation(link, "doctor", "com.se100.clinic.doctor.Doctor", "doctor_id");
    assertLazyAssociation(link, "specialty", "com.se100.clinic.doctor.Specialty", "specialty_id");
    assertThat(table.getForeignKeyCollection())
        .extracting(key -> key.getReferencedTable().getName())
        .containsExactlyInAnyOrder("doctors", "specialties");
  }

  private static void assertExistingSpecialtyMapping(MetadataImplementor metadata) {
    Table table = metadata.getEntityBinding("com.se100.clinic.doctor.Specialty").getTable();

    assertThat(table.getName()).isEqualTo("specialties");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id", "code", "name", "description", "active", "created_at", "updated_at");
    assertThat(column(table, "code").getLength()).isEqualTo(50L);
    assertThat(column(table, "name").getLength()).isEqualTo(200L);
  }

  private static void assertLazyAssociation(
      PersistentClass entity, String propertyName, String target, String columnName) {
    var property = entity.getProperty(propertyName);
    var association = (ToOne) property.getValue();
    assertThat(association.isLazy()).isTrue();
    assertThat(association.getReferencedEntityName()).isEqualTo(target);
    assertThat(association.getColumns()).extracting(Column::getName).containsExactly(columnName);
    assertThat(property.getCascade()).isEqualTo("none");
  }

  private static Column column(Table table, String name) {
    return table.getColumns().stream()
        .filter(column -> column.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
