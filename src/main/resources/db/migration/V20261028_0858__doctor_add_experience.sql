-- Module: doctor
-- Free-text description of the doctor's professional experience. Optional.

alter table doctors
  add column experience text;
