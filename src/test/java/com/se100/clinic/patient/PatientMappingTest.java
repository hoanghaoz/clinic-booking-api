package com.se100.clinic.patient;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import java.util.List;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.ToOne;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** Verifies PostgreSQL patient mappings without connecting to a database or creating a schema. */
class PatientMappingTest {

  @Test
  void bootstrap_shouldMatchPatientSchema_whenEntitiesAreDiscovered()
      throws ClassNotFoundException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    var entityNames =
        scanner.findCandidateComponents("com.se100.clinic.patient").stream()
            .map(definition -> definition.getBeanClassName())
            .toList();
    assertThat(entityNames)
        .containsExactlyInAnyOrder(
            "com.se100.clinic.patient.Patient", "com.se100.clinic.patient.PatientMedicalHistory");

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
        assertThat(sessionFactory.getMetamodel().getEntities()).hasSize(2);
        assertPatientMapping(metadata);
        assertMedicalHistoryMapping(metadata);
      }
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  private static void assertPatientMapping(MetadataImplementor metadata) {
    var patient = metadata.getEntityBinding("com.se100.clinic.patient.Patient");
    Table table = patient.getTable();

    assertThat(table.getName()).isEqualTo("patients");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id",
            "public_id",
            "user_id",
            "full_name",
            "date_of_birth",
            "gender",
            "phone",
            "contact_email",
            "address",
            "identity_type",
            "identity_number",
            "identity_issuer",
            "class",
            "card_number",
            "card_activated_at",
            "card_expires_at",
            "card_revoked_at",
            "created_at",
            "updated_at");
    assertThat(table.getColumns().stream().filter(Column::isNullable))
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "user_id",
            "contact_email",
            "card_number",
            "card_activated_at",
            "card_expires_at",
            "card_revoked_at");
    assertThat(column(table, "id").isIdentity()).isTrue();
    assertThat(column(table, "public_id").getSqlType(metadata)).isEqualTo("uuid");
    assertThat(column(table, "date_of_birth").getSqlType(metadata)).isEqualTo("date");
    assertThat(column(table, "address").getSqlType(metadata)).isEqualTo("text");
    assertThat(column(table, "full_name").getLength()).isEqualTo(150L);
    assertThat(column(table, "gender").getLength()).isEqualTo(20L);
    assertThat(column(table, "phone").getLength()).isEqualTo(25L);
    assertThat(column(table, "contact_email").getLength()).isEqualTo(254L);
    assertThat(column(table, "identity_type").getLength()).isEqualTo(20L);
    assertThat(column(table, "identity_number").getLength()).isEqualTo(40L);
    assertThat(column(table, "identity_issuer").getLength()).isEqualTo(80L);
    assertThat(column(table, "card_number").getLength()).isEqualTo(40L);
    assertThat(table.getColumns().stream().filter(Column::isUnique))
        .extracting(Column::getName)
        .containsExactlyInAnyOrder("public_id", "user_id", "card_number");
    assertThat(table.getUniqueKeys().values())
        .extracting(key -> key.getColumns().stream().map(Column::getName).toList())
        .contains(List.of("identity_type", "identity_issuer", "identity_number"));
    assertThat(table.getIndexes().values())
        .extracting(index -> index.getSelectables().stream().map(Selectable::getText).toList())
        .containsExactly(List.of("phone"));
    assertThat(column(table, "class").getDefaultValue()).isEqualTo("'PERSONAL'");
    assertThat(((BasicValue) patient.getProperty("patientClass").getValue()).getEnumeratedType())
        .isEqualTo(EnumType.STRING);
    for (String name : List.of("card_activated_at", "card_expires_at", "card_revoked_at")) {
      assertThat(column(table, name).getSqlType(metadata))
          .matches("timestamp\\(\\d+\\) with time zone");
    }
    assertThat(table.getForeignKeyCollection()).isEmpty();
  }

  private static void assertMedicalHistoryMapping(MetadataImplementor metadata) {
    var history = metadata.getEntityBinding("com.se100.clinic.patient.PatientMedicalHistory");
    Table table = history.getTable();

    assertThat(table.getName()).isEqualTo("patient_medical_histories");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id",
            "patient_id",
            "type",
            "description",
            "family_relationship",
            "recorded_by_staff_id",
            "recorded_by_doctor_id",
            "recorded_at",
            "updated_at");
    assertThat(table.getColumns().stream().filter(Column::isNullable))
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "family_relationship", "recorded_by_staff_id", "recorded_by_doctor_id");
    assertThat(column(table, "id").isIdentity()).isTrue();
    assertThat(column(table, "description").getSqlType(metadata)).isEqualTo("text");
    assertThat(column(table, "family_relationship").getLength()).isEqualTo(50L);
    for (String name : List.of("recorded_at", "updated_at")) {
      assertThat(column(table, name).getSqlType(metadata))
          .matches("timestamp\\(\\d+\\) with time zone");
    }
    assertThat(((BasicValue) history.getProperty("type").getValue()).getEnumeratedType())
        .isEqualTo(EnumType.STRING);
    assertThat(table.getIndexes().values())
        .extracting(index -> index.getSelectables().stream().map(Selectable::getText).toList())
        .containsExactly(List.of("patient_id", "type"));
    var patientProperty = history.getProperty("patient");
    var patient = (ToOne) patientProperty.getValue();
    assertThat(patient.isLazy()).isTrue();
    assertThat(patient.getReferencedEntityName()).isEqualTo("com.se100.clinic.patient.Patient");
    assertThat(patientProperty.getCascade()).isEqualTo("none");
    assertThat(table.getForeignKeyCollection())
        .singleElement()
        .satisfies(
            key -> {
              assertThat(key.getReferencedTable().getName()).isEqualTo("patients");
              assertThat(key.getColumns())
                  .extracting(Column::getName)
                  .containsExactly("patient_id");
            });
  }

  private static Column column(Table table, String name) {
    return table.getColumns().stream()
        .filter(column -> column.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
