-- Unique(date, shift) cannot detect two different shifts whose time ranges overlap.
create extension if not exists btree_gist;

alter table doctor_schedules drop constraint uk_doctor_schedules_doctor_date_shift;
alter table doctor_schedules drop constraint uk_doctor_schedules_room_date_shift;

alter table doctor_schedules
  add constraint ck_doctor_schedules_time_order check (start_at < end_at);
alter table doctor_schedule_slots
  add constraint ck_doctor_schedule_slots_time_order check (start_at < end_at);

alter table doctor_schedules
  add constraint ex_doctor_schedules_doctor_overlap
  exclude using gist (doctor_id with =, tstzrange(start_at, end_at, '[)') with &&)
  where (status <> 'CANCELLED');

alter table doctor_schedules
  add constraint ex_doctor_schedules_room_overlap
  exclude using gist (room_id with =, tstzrange(start_at, end_at, '[)') with &&)
  where (status <> 'CANCELLED');
