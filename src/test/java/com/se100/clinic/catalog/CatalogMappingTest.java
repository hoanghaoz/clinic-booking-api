package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

/** Verifies catalog mappings without database access, DDL, or schema mutation. */
class CatalogMappingTest {

  @Test
  void bootstrap_shouldMatchCatalogSchema_whenEntitiesAreDiscovered()
      throws ClassNotFoundException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    var entityNames =
        scanner.findCandidateComponents("com.se100.clinic.catalog").stream()
            .map(definition -> definition.getBeanClassName())
            .toList();
    assertThat(entityNames)
        .containsExactlyInAnyOrder(
            "com.se100.clinic.catalog.MedicalService",
            "com.se100.clinic.catalog.MedicalItem",
            "com.se100.clinic.catalog.MedicalServiceItem",
            "com.se100.clinic.catalog.MedicalPackage",
            "com.se100.clinic.catalog.PackageSpeciality",
            "com.se100.clinic.catalog.PackageVersion",
            "com.se100.clinic.catalog.PackageVersionItem");

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
        assertThat(sessionFactory.getMetamodel().getEntities()).hasSize(7);
        assertMedicalService(metadata);
        assertMedicalItem(metadata);
        assertMedicalPackage(metadata);
        assertServiceItem(metadata);
        assertPackageSpeciality(metadata);
        assertPackageVersion(metadata);
        assertVersionItem(metadata);
      }
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  private static void assertMedicalService(MetadataImplementor metadata) {
    var entity = entity(metadata, "MedicalService");
    Table table = entity.getTable();
    assertTable(
        table,
        "medical_services",
        "id",
        "public_id",
        "description",
        "name",
        "price",
        "duration_minutes",
        "target",
        "min_age",
        "max_age",
        "instruction",
        "created_at",
        "updated_at");
    assertNullable(table, "description", "min_age", "max_age", "instruction");
    assertIdentityUuid(metadata, table);
    assertUniqueColumns(table, "public_id");
    assertLength(table, "name", 200);
    assertText(metadata, table, "description", "instruction");
    assertMoney(metadata, entity, "price", "price");
    assertThat(column(table, "duration_minutes").getSqlType(metadata)).isEqualTo("integer");
    assertThat(column(table, "min_age").getSqlType(metadata)).isEqualTo("integer");
    assertThat(column(table, "max_age").getSqlType(metadata)).isEqualTo("integer");
    assertEnum(entity, "target", "target", "TargetGender", "'BOTH'");
    assertTimestamps(metadata, table, "created_at", "updated_at");
    assertThat(table.getForeignKeyCollection()).isEmpty();
  }

  private static void assertMedicalItem(MetadataImplementor metadata) {
    var entity = entity(metadata, "MedicalItem");
    Table table = entity.getTable();
    assertTable(
        table,
        "medical_items",
        "id",
        "public_id",
        "name",
        "type",
        "price",
        "description",
        "instruction",
        "created_at",
        "updated_at");
    assertNullable(table, "description", "instruction");
    assertIdentityUuid(metadata, table);
    assertUniqueColumns(table, "public_id");
    assertLength(table, "name", 200);
    assertText(metadata, table, "description", "instruction");
    assertMoney(metadata, entity, "price", "price");
    assertEnum(entity, "type", "type", "MedicalItemType", null);
    assertTimestamps(metadata, table, "created_at", "updated_at");
    assertThat(table.getForeignKeyCollection()).isEmpty();
  }

  private static void assertMedicalPackage(MetadataImplementor metadata) {
    Table table = entity(metadata, "MedicalPackage").getTable();
    assertTable(
        table, "medical_packages", "id", "public_id", "code", "name", "created_at", "updated_at");
    assertNullable(table);
    assertIdentityUuid(metadata, table);
    assertUniqueColumns(table, "public_id", "code");
    assertLength(table, "code", 40);
    assertLength(table, "name", 200);
    assertTimestamps(metadata, table, "created_at", "updated_at");
    assertThat(table.getForeignKeyCollection()).isEmpty();
  }

  private static void assertServiceItem(MetadataImplementor metadata) {
    var entity = entity(metadata, "MedicalServiceItem");
    Table table = entity.getTable();
    assertTable(table, "medical_service_items", "service_id", "item_id", "quantity", "created_at");
    assertNullable(table);
    assertCompositeId(
        entity, "MedicalServiceItemId", Map.of("serviceId", "service_id", "itemId", "item_id"));
    assertAssociation(entity, "service", "MedicalService", "service_id");
    assertAssociation(entity, "item", "MedicalItem", "item_id");
    assertForeignTables(table, "medical_services", "medical_items");
    assertThat(column(table, "quantity").getDefaultValue()).isEqualTo("1");
    assertTimestamps(metadata, table, "created_at");
  }

  private static void assertPackageSpeciality(MetadataImplementor metadata) {
    var entity = entity(metadata, "PackageSpeciality");
    Table table = entity.getTable();
    assertTable(table, "package_specialities", "package_id", "specialty_id", "created_at");
    assertNullable(table);
    assertCompositeId(
        entity,
        "PackageSpecialityId",
        Map.of("packageId", "package_id", "specialtyId", "specialty_id"));
    assertAssociation(entity, "medicalPackage", "MedicalPackage", "package_id");
    assertForeignTables(table, "medical_packages");
    assertTimestamps(metadata, table, "created_at");
  }

  private static void assertPackageVersion(MetadataImplementor metadata) {
    var entity = entity(metadata, "PackageVersion");
    Table table = entity.getTable();
    assertTable(
        table,
        "package_versions",
        "id",
        "package_id",
        "code",
        "name",
        "target",
        "min_age",
        "max_age",
        "description",
        "instruction",
        "version",
        "unit_price",
        "class",
        "status",
        "consultation_minutes",
        "effective_from",
        "effective_to",
        "created_at");
    assertNullable(table, "min_age", "max_age", "description", "instruction", "effective_to");
    assertThat(column(table, "id").isIdentity()).isTrue();
    assertUniqueColumns(table);
    assertLength(table, "code", 50);
    assertLength(table, "name", 200);
    assertLength(table, "version", 30);
    assertText(metadata, table, "description", "instruction");
    assertMoney(metadata, entity, "unitPrice", "unit_price");
    assertThat(column(table, "consultation_minutes").getSqlType(metadata)).isEqualTo("integer");
    assertThat(column(table, "min_age").getSqlType(metadata)).isEqualTo("integer");
    assertThat(column(table, "max_age").getSqlType(metadata)).isEqualTo("integer");
    assertEnum(entity, "target", "target", "TargetGender", "'BOTH'");
    assertEnum(entity, "packageClass", "class", "PackageClass", null);
    assertEnum(entity, "status", "status", "PackageStatus", null);
    assertThat(table.getUniqueKeys().values())
        .extracting(key -> key.getColumns().stream().map(Column::getName).toList())
        .contains(List.of("package_id", "version"));
    assertAssociation(entity, "medicalPackage", "MedicalPackage", "package_id");
    assertForeignTables(table, "medical_packages");
    assertTimestamps(metadata, table, "effective_from", "effective_to", "created_at");
  }

  private static void assertVersionItem(MetadataImplementor metadata) {
    var entity = entity(metadata, "PackageVersionItem");
    Table table = entity.getTable();
    assertTable(
        table,
        "package_version_items",
        "package_version_id",
        "item_id",
        "item_name_snapshot",
        "instruction_snapshot",
        "quantity",
        "position");
    assertNullable(table, "instruction_snapshot");
    assertCompositeId(
        entity,
        "PackageVersionItemId",
        Map.of("packageVersionId", "package_version_id", "itemId", "item_id"));
    assertAssociation(entity, "packageVersion", "PackageVersion", "package_version_id");
    assertAssociation(entity, "item", "MedicalItem", "item_id");
    assertForeignTables(table, "package_versions", "medical_items");
    assertLength(table, "item_name_snapshot", 200);
    assertText(metadata, table, "instruction_snapshot");
    assertThat(column(table, "quantity").getDefaultValue()).isEqualTo("1");
    assertThat(column(table, "position").getSqlType(metadata)).isEqualTo("integer");
    assertThat(column(table, "position").getDefaultValue()).isNull();
  }

  private static PersistentClass entity(MetadataImplementor metadata, String name) {
    return metadata.getEntityBinding("com.se100.clinic.catalog." + name);
  }

  private static void assertTable(Table table, String name, String... columns) {
    assertThat(table.getName()).isEqualTo(name);
    assertThat(table.getColumns()).extracting(Column::getName).containsExactlyInAnyOrder(columns);
  }

  private static void assertNullable(Table table, String... columns) {
    assertThat(table.getColumns().stream().filter(Column::isNullable))
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(columns);
  }

  private static void assertIdentityUuid(MetadataImplementor metadata, Table table) {
    assertThat(column(table, "id").isIdentity()).isTrue();
    assertThat(column(table, "public_id").getSqlType(metadata)).isEqualTo("uuid");
  }

  private static void assertUniqueColumns(Table table, String... names) {
    assertThat(table.getColumns().stream().filter(Column::isUnique))
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(names);
  }

  private static void assertLength(Table table, String name, long length) {
    assertThat(column(table, name).getLength()).isEqualTo(length);
  }

  private static void assertText(MetadataImplementor metadata, Table table, String... names) {
    for (String name : names) {
      assertThat(column(table, name).getSqlType(metadata)).isEqualTo("text");
    }
  }

  private static void assertMoney(
      MetadataImplementor metadata, PersistentClass entity, String property, String columnName) {
    Column column = column(entity.getTable(), columnName);
    assertThat(column.getPrecision()).isEqualTo(15);
    assertThat(column.getScale()).isEqualTo(2);
    assertThat(column.getSqlType(metadata)).isEqualTo("numeric(15,2)");
    assertThat(entity.getProperty(property).getType().getReturnedClass())
        .isEqualTo(BigDecimal.class);
  }

  private static void assertEnum(
      PersistentClass entity,
      String property,
      String columnName,
      String enumName,
      String defaultValue) {
    var value = (BasicValue) entity.getProperty(property).getValue();
    assertThat(value.getEnumeratedType()).isEqualTo(EnumType.STRING);
    assertThat(value.getType().getReturnedClass().getName())
        .isEqualTo("com.se100.clinic.catalog.enums." + enumName);
    assertThat(column(entity.getTable(), columnName).getDefaultValue()).isEqualTo(defaultValue);
  }

  private static void assertCompositeId(
      PersistentClass entity, String idClass, Map<String, String> columns) {
    var id = (Component) entity.getIdentifier();
    assertThat(id.getComponentClassName()).isEqualTo("com.se100.clinic.catalog." + idClass);
    assertThat(id.getProperties())
        .extracting(property -> property.getName())
        .containsExactlyInAnyOrderElementsOf(columns.keySet());
    assertThat(entity.getTable().getPrimaryKey().getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrderElementsOf(columns.values());
    columns.forEach(
        (property, name) ->
            assertThat(id.getProperty(property).getValue().getColumns())
                .extracting(Column::getName)
                .containsExactly(name));
  }

  private static void assertAssociation(
      PersistentClass entity, String propertyName, String target, String columnName) {
    var property = entity.getProperty(propertyName);
    var association = (ToOne) property.getValue();
    assertThat(association.isLazy()).isTrue();
    assertThat(association.getReferencedEntityName())
        .isEqualTo("com.se100.clinic.catalog." + target);
    assertThat(association.getColumns()).extracting(Column::getName).containsExactly(columnName);
    assertThat(property.getCascade()).isEqualTo("none");
  }

  private static void assertForeignTables(Table table, String... names) {
    assertThat(table.getForeignKeyCollection())
        .extracting(key -> key.getReferencedTable().getName())
        .containsExactlyInAnyOrder(names);
  }

  private static void assertTimestamps(MetadataImplementor metadata, Table table, String... names) {
    for (String name : names) {
      assertThat(column(table, name).getSqlType(metadata))
          .matches("timestamptz|timestamp(?:\\(\\d+\\))? with time zone");
    }
  }

  private static Column column(Table table, String name) {
    return table.getColumns().stream()
        .filter(column -> column.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
