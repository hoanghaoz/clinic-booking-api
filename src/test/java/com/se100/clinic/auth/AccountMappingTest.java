package com.se100.clinic.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.ToOne;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Boots the account mappings without connecting to a database or executing DDL. */
class AccountMappingTest {

  private static StandardServiceRegistry registry;
  private static MetadataImplementor metadata;
  private static SessionFactory sessionFactory;

  @BeforeAll
  static void bootstrap() {
    registry =
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
    metadata =
        (MetadataImplementor)
            new MetadataSources(registry)
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(RefreshToken.class)
                .buildMetadata();
    sessionFactory = metadata.buildSessionFactory();
  }

  @AfterAll
  static void close() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
    if (registry != null) {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  @Test
  void user_shouldMatchAccountSchema_whenMetadataBootstraps() {
    Table users = metadata.getEntityBinding(User.class.getName()).getTable();

    assertThat(users.getName()).isEqualTo("users");
    assertThat(users.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id",
            "public_id",
            "email",
            "username",
            "password_hashed",
            "role",
            "status",
            "created_at",
            "updated_at");
    assertThat(users.getColumns()).allMatch(column -> !column.isNullable());
    assertThat(column(users, "email").getLength()).isEqualTo(254L);
    assertThat(column(users, "username").getLength()).isEqualTo(100L);
    assertThat(column(users, "password_hashed").getSqlType(metadata)).isEqualTo("text");
    assertThat(column(users, "public_id").getSqlType(metadata)).isEqualTo("uuid");
    assertThat(column(users, "created_at").getSqlType(metadata)).isEqualTo("timestamptz");
    assertThat(column(users, "updated_at").getSqlType(metadata)).isEqualTo("timestamptz");
    assertThat(column(users, "status").getDefaultValue()).isEqualTo("'ACTIVE'");
    assertThat(users.getColumns().stream().filter(Column::isUnique))
        .extracting(Column::getName)
        .containsExactlyInAnyOrder("public_id", "email", "username");
  }

  @Test
  void refreshToken_shouldMatchTokenSchema_whenMetadataBootstraps() {
    Table tokens = metadata.getEntityBinding(RefreshToken.class.getName()).getTable();

    assertThat(tokens.getName()).isEqualTo("refresh_tokens");
    assertThat(tokens.getColumns())
        .extracting(Column::getName)
        .containsExactlyInAnyOrder(
            "id", "user_id", "token_hash", "expires_at", "revoked_at", "created_at");
    assertThat(tokens.getColumns().stream().filter(Column::isNullable))
        .extracting(Column::getName)
        .containsExactly("revoked_at");
    assertThat(column(tokens, "token_hash").getLength()).isEqualTo(128L);
    assertThat(column(tokens, "expires_at").getSqlType(metadata)).isEqualTo("timestamptz");
    assertThat(column(tokens, "revoked_at").getSqlType(metadata)).isEqualTo("timestamptz");
    assertThat(column(tokens, "created_at").getSqlType(metadata)).isEqualTo("timestamptz");
    assertThat(tokens.getColumns().stream().filter(Column::isUnique))
        .extracting(Column::getName)
        .containsExactly("token_hash");
    assertThat(tokens.getIndexes().values())
        .extracting(index -> index.getSelectables().stream().map(Selectable::getText).toList())
        .containsExactlyInAnyOrder(List.of("user_id", "revoked_at"), List.of("expires_at"));
  }

  @Test
  void refreshToken_shouldReferenceUserLazilyWithoutCascade_whenMetadataBootstraps() {
    var tokenMapping = metadata.getEntityBinding(RefreshToken.class.getName());
    var userProperty = tokenMapping.getProperty("user");

    assertThat(userProperty.getValue()).isInstanceOf(ToOne.class);
    ToOne user = (ToOne) userProperty.getValue();
    assertThat(user.isLazy()).isTrue();
    assertThat(user.getReferencedEntityName()).isEqualTo(User.class.getName());
    assertThat(userProperty.getCascade()).isEqualTo("none");
    assertThat(tokenMapping.getTable().getForeignKeyCollection())
        .singleElement()
        .satisfies(
            key -> {
              assertThat(key.getReferencedTable().getName()).isEqualTo("users");
              assertThat(key.getColumns()).extracting(Column::getName).containsExactly("user_id");
            });
  }

  private static Column column(Table table, String name) {
    return table.getColumns().stream()
        .filter(column -> column.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
