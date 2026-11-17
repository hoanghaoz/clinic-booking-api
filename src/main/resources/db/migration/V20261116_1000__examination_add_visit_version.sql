-- Module: examination
-- Optimistic-lock counter so two tabs saving the same visit cannot silently overwrite each other.
alter table medical_visits add column version bigint not null default 0;
