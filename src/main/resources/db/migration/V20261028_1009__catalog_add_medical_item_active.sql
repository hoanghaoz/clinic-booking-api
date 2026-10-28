-- Module: catalog
-- Soft delete for medical items: they are referenced by package versions, services and medical
-- orders, so they are deactivated instead of removed. Existing items stay active.

alter table medical_items
  add column active boolean not null default true;
