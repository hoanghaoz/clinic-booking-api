package com.se100.clinic.facility;

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
import org.hibernate.mapping.Table;
import org.hibernate.mapping.ToOne;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** Verifies PostgreSQL facility mappings without database access or schema creation. */
class FacilityMappingTest {

  @Test
  void bootstrap_shouldMatchFacilitySchema_whenEntitiesAreDiscovered()
      throws ClassNotFoundException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    var entityNames =
        scanner.findCandidateComponents("com.se100.clinic.facility").stream()
            .map(definition -> definition.getBeanClassName())
            .toList();
    assertThat(entityNames)
        .containsExactlyInAnyOrder(
            "com.se100.clinic.facility.Clinic", "com.se100.clinic.facility.Room");

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
        assertClinicMapping(metadata);
        assertRoomMapping(metadata);
      }
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  private static void assertClinicMapping(MetadataImplementor metadata) {
    var clinic = metadata.getEntityBinding("com.se100.clinic.facility.Clinic");
    Table table = clinic.getTable();

    assertThat(table.getName()).isEqualTo("clinics");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id",
            "public_id",
            "code",
            "description",
            "name",
            "address",
            "phone_number",
            "email",
            "status",
            "created_at",
            "updated_at");
    assertThat(table.getColumns().stream().filter(Column::isNullable))
        .extracting(Column::getName)
        .containsExactlyInAnyOrder("description", "email");
    assertThat(column(table, "id").isIdentity()).isTrue();
    assertThat(column(table, "public_id").getSqlType(metadata)).isEqualTo("uuid");
    assertThat(column(table, "description").getSqlType(metadata)).isEqualTo("text");
    assertThat(column(table, "address").getSqlType(metadata)).isEqualTo("text");
    assertThat(column(table, "code").getLength()).isEqualTo(30L);
    assertThat(column(table, "name").getLength()).isEqualTo(150L);
    assertThat(column(table, "phone_number").getLength()).isEqualTo(25L);
    assertThat(column(table, "email").getLength()).isEqualTo(254L);
    assertThat(table.getColumns().stream().filter(Column::isUnique))
        .extracting(Column::getName)
        .containsExactlyInAnyOrder("public_id", "code");
    assertThat(((BasicValue) clinic.getProperty("status").getValue()).getEnumeratedType())
        .isEqualTo(EnumType.STRING);
    assertThat(column(table, "status").getDefaultValue()).isNull();
    assertThat(table.getForeignKeyCollection()).isEmpty();
  }

  private static void assertRoomMapping(MetadataImplementor metadata) {
    var room = metadata.getEntityBinding("com.se100.clinic.facility.Room");
    Table table = room.getTable();

    assertThat(table.getName()).isEqualTo("rooms");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id", "public_id", "code", "clinic_id", "name", "status", "created_at", "updated_at");
    assertThat(table.getColumns()).allMatch(column -> !column.isNullable());
    assertThat(column(table, "id").isIdentity()).isTrue();
    assertThat(column(table, "public_id").getSqlType(metadata)).isEqualTo("uuid");
    assertThat(column(table, "code").getLength()).isEqualTo(30L);
    assertThat(column(table, "name").getLength()).isEqualTo(150L);
    assertThat(table.getColumns().stream().filter(Column::isUnique))
        .extracting(Column::getName)
        .containsExactly("public_id");
    assertThat(table.getUniqueKeys().values())
        .extracting(key -> key.getColumns().stream().map(Column::getName).toList())
        .contains(List.of("clinic_id", "code"));
    assertThat(((BasicValue) room.getProperty("status").getValue()).getEnumeratedType())
        .isEqualTo(EnumType.STRING);
    assertThat(column(table, "status").getDefaultValue()).isNull();
    var clinicProperty = room.getProperty("clinic");
    var clinic = (ToOne) clinicProperty.getValue();
    assertThat(clinic.isLazy()).isTrue();
    assertThat(clinic.getReferencedEntityName()).isEqualTo("com.se100.clinic.facility.Clinic");
    assertThat(clinicProperty.getCascade()).isEqualTo("none");
    assertThat(table.getForeignKeyCollection())
        .singleElement()
        .satisfies(
            key -> {
              assertThat(key.getReferencedTable().getName()).isEqualTo("clinics");
              assertThat(key.getColumns()).extracting(Column::getName).containsExactly("clinic_id");
            });
  }

  private static Column column(Table table, String name) {
    return table.getColumns().stream()
        .filter(column -> column.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
