package com.se100.clinic.schedule;

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
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.ToOne;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** Verifies PostgreSQL schedule mappings without database access or schema creation. */
class ScheduleMappingTest {

  @Test
  void bootstrap_shouldMatchScheduleSchema_whenEntitiesAreDiscovered()
      throws ClassNotFoundException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    var entityNames =
        scanner.findCandidateComponents("com.se100.clinic.schedule").stream()
            .map(definition -> definition.getBeanClassName())
            .toList();
    assertThat(entityNames)
        .containsExactlyInAnyOrder(
            "com.se100.clinic.schedule.Shift",
            "com.se100.clinic.schedule.DoctorSchedule",
            "com.se100.clinic.schedule.DoctorScheduleSlot");

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
        assertShiftMapping(metadata);
        assertScheduleMapping(metadata);
        assertSlotMapping(metadata);
      }
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  private static void assertShiftMapping(MetadataImplementor metadata) {
    var shift = metadata.getEntityBinding("com.se100.clinic.schedule.Shift");
    Table table = shift.getTable();

    assertThat(table.getName()).isEqualTo("shifts");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id", "code", "name", "start_time", "end_time", "created_at", "updated_at");
    assertThat(table.getColumns()).allMatch(column -> !column.isNullable());
    assertThat(column(table, "id").isIdentity()).isTrue();
    assertThat(column(table, "code").getLength()).isEqualTo(30L);
    assertThat(column(table, "name").getLength()).isEqualTo(100L);
    assertThat(table.getColumns().stream().filter(Column::isUnique))
        .extracting(Column::getName)
        .containsExactly("code");
    for (String name : List.of("start_time", "end_time")) {
      assertThat(column(table, name).getSqlType(metadata))
          .matches("time(?:\\(\\d+\\))?(?: without time zone)?");
    }
    assertThat(table.getForeignKeyCollection()).isEmpty();
    assertTimestamps(metadata, table, "created_at", "updated_at");
  }

  private static void assertScheduleMapping(MetadataImplementor metadata) {
    var schedule = metadata.getEntityBinding("com.se100.clinic.schedule.DoctorSchedule");
    Table table = schedule.getTable();

    assertThat(table.getName()).isEqualTo("doctor_schedules");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id",
            "public_id",
            "doctor_id",
            "specialty_id",
            "room_id",
            "shift_id",
            "work_date",
            "start_at",
            "end_at",
            "general_capacity",
            "specialist_capacity",
            "status",
            "created_at",
            "updated_at");
    assertThat(table.getColumns()).allMatch(column -> !column.isNullable());
    assertThat(column(table, "id").isIdentity()).isTrue();
    assertThat(column(table, "public_id").getSqlType(metadata)).isEqualTo("uuid");
    assertThat(column(table, "work_date").getSqlType(metadata)).isEqualTo("date");
    assertThat(table.getColumns().stream().filter(Column::isUnique))
        .extracting(Column::getName)
        .containsExactly("public_id");
    // Exclusion constraints covering real time overlap live in Flyway, not JPA metadata.
    assertThat(table.getUniqueKeys()).isEmpty();
    assertThat(table.getIndexes().values())
        .extracting(index -> index.getSelectables().stream().map(Selectable::getText).toList())
        .containsExactly(List.of("work_date", "status"));
    assertStatus(schedule, "com.se100.clinic.schedule.enums.ScheduleStatus", "'OPEN'");
    assertLazyAssociation(schedule, "shift", "com.se100.clinic.schedule.Shift", "shift_id");
    assertThat(table.getForeignKeyCollection())
        .singleElement()
        .satisfies(key -> assertThat(key.getReferencedTable().getName()).isEqualTo("shifts"));
    assertTimestamps(metadata, table, "start_at", "end_at", "created_at", "updated_at");
  }

  private static void assertSlotMapping(MetadataImplementor metadata) {
    var slot = metadata.getEntityBinding("com.se100.clinic.schedule.DoctorScheduleSlot");
    Table table = slot.getTable();

    assertThat(table.getName()).isEqualTo("doctor_schedule_slots");
    assertThat(table.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id", "schedule_id", "start_at", "end_at", "status", "created_at", "updated_at");
    assertThat(table.getColumns()).allMatch(column -> !column.isNullable());
    assertThat(column(table, "id").isIdentity()).isTrue();
    assertThat(table.getUniqueKeys().values())
        .extracting(key -> key.getColumns().stream().map(Column::getName).toList())
        .contains(List.of("schedule_id", "start_at"));
    assertStatus(slot, "com.se100.clinic.schedule.enums.SlotStatus", "'AVAILABLE'");
    assertLazyAssociation(
        slot, "schedule", "com.se100.clinic.schedule.DoctorSchedule", "schedule_id");
    assertThat(table.getForeignKeyCollection())
        .singleElement()
        .satisfies(
            key -> assertThat(key.getReferencedTable().getName()).isEqualTo("doctor_schedules"));
    assertTimestamps(metadata, table, "start_at", "end_at", "created_at", "updated_at");
  }

  private static void assertStatus(PersistentClass entity, String enumClass, String defaultValue) {
    var status = (BasicValue) entity.getProperty("status").getValue();
    assertThat(status.getEnumeratedType()).isEqualTo(EnumType.STRING);
    assertThat(status.getType().getReturnedClass().getName()).isEqualTo(enumClass);
    assertThat(column(entity.getTable(), "status").getDefaultValue()).isEqualTo(defaultValue);
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
