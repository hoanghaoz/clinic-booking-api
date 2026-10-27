-- Module: catalog
-- Item names are matched exactly (after trimming in the service), so the database is the last
-- line of defence against two requests creating the same item at the same time.
-- Fails if medical_items already holds duplicate names: clean them up first.

alter table medical_items
  add constraint uk_medical_items_name unique (name);
