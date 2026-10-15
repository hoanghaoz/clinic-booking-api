package com.se100.clinic.employee;

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
import org.hibernate.mapping.Table;
import org.hibernate.mapping.ToOne;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** Verifies entity discovery and PostgreSQL mappings without a database or schema creation. */
class EmployeeMappingTest {

  @Test
  void bootstrap_shouldMatchEmployeeSchema_whenEntitiesAreDiscovered()
      throws ClassNotFoundException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    var entityNames =
        scanner.findCandidateComponents("com.se100.clinic.employee").stream()
            .map(definition -> definition.getBeanClassName())
            .toList();
    assertThat(entityNames)
        .containsExactlyInAnyOrder(
            "com.se100.clinic.employee.Staff", "com.se100.clinic.employee.ClinicStaff");

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
        assertStaffMapping(metadata);
        assertClinicStaffMapping(metadata);
      }
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  private static void assertStaffMapping(MetadataImplementor metadata) {
    var staff = metadata.getEntityBinding("com.se100.clinic.employee.Staff");
    Table table = staff.getTable();

    assertThat(table.getName()).isEqualTo("staffs");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id", "public_id", "user_id", "name", "status", "created_at", "updated_at");
    assertThat(table.getColumns()).allMatch(column -> !column.isNullable());
    assertThat(table.getPrimaryKey().getColumns())
        .extracting(Column::getName)
        .containsExactly("id");
    assertThat(column(table, "id").isIdentity()).isTrue();
    assertThat(column(table, "public_id").getSqlType(metadata)).isEqualTo("uuid");
    assertThat(table.getColumns().stream().filter(Column::isUnique))
        .extracting(Column::getName)
        .containsExactlyInAnyOrder("public_id", "user_id");
    assertThat(column(table, "name").getLength()).isEqualTo(150L);
    assertThat(column(table, "created_at").getSqlType(metadata)).isEqualTo("timestamptz");
    assertThat(column(table, "updated_at").getSqlType(metadata)).isEqualTo("timestamptz");
    assertThat(column(table, "status").getDefaultValue()).isNull();
    var status = (BasicValue) staff.getProperty("status").getValue();
    assertThat(status.getEnumeratedType()).isEqualTo(EnumType.STRING);
    assertThat(table.getForeignKeyCollection()).isEmpty();
  }

  private static void assertClinicStaffMapping(MetadataImplementor metadata) {
    var clinicStaff = metadata.getEntityBinding("com.se100.clinic.employee.ClinicStaff");
    Table table = clinicStaff.getTable();

    assertThat(table.getName()).isEqualTo("clinic_staffs");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder("staff_id", "clinic_id");
    assertThat(table.getColumns()).allMatch(column -> !column.isNullable());
    assertThat(table.getPrimaryKey().getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder("staff_id", "clinic_id");
    assertThat(table.getIndexes()).isEmpty();
    assertThat(table.getUniqueKeys()).isEmpty();

    var id = (Component) clinicStaff.getIdentifier();
    assertThat(id.getProperties())
        .extracting(property -> property.getName())
        .containsExactlyInAnyOrder("staffId", "clinicId");
    assertThat(id.getProperty("staffId").getValue().getColumns())
        .extracting(Column::getName)
        .containsExactly("staff_id");
    assertThat(id.getProperty("clinicId").getValue().getColumns())
        .extracting(Column::getName)
        .containsExactly("clinic_id");
    var staffProperty = clinicStaff.getProperty("staff");
    var staff = (ToOne) staffProperty.getValue();
    assertThat(staff.isLazy()).isTrue();
    assertThat(staff.getReferencedEntityName()).isEqualTo("com.se100.clinic.employee.Staff");
    assertThat(staffProperty.getCascade()).isEqualTo("none");
    assertThat(table.getForeignKeyCollection())
        .singleElement()
        .satisfies(
            key -> {
              assertThat(key.getReferencedTable().getName()).isEqualTo("staffs");
              assertThat(key.getColumns()).extracting(Column::getName).containsExactly("staff_id");
            });
  }

  private static Column column(Table table, String name) {
    return table.getColumns().stream()
        .filter(column -> column.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
