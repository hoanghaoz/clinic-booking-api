-- Module: catalog
-- The list/search query only reads the current version of each package (effective_to is null),
-- so a partial index on the common filter columns stays small. Not declared on the entity because
-- JPA @Index cannot express a WHERE clause.
create index idx_package_versions_current on package_versions (status, unit_price)
  where effective_to is null;
