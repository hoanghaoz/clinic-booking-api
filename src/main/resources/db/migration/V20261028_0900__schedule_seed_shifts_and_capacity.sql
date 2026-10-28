-- Fixed clinic shifts from FR-23 / UC-12. Do not change codes once schedules reference them.
insert into shifts (code, name, start_time, end_time)
values
  ('MORNING', 'Ca sáng', '08:00', '12:00'),
  ('AFTERNOON', 'Ca chiều', '13:00', '17:00'),
  ('EVENING', 'Ca ngoài giờ', '17:00', '20:00')
on conflict (code) do nothing;

-- Existing schedules, if any, inherit the physical maximum of their shift.
alter table doctor_schedules add column general_capacity integer;
alter table doctor_schedules add column specialist_capacity integer;

update doctor_schedules ds
set general_capacity = floor(extract(epoch from (s.end_time - s.start_time)) / 1200),
    specialist_capacity = floor(extract(epoch from (s.end_time - s.start_time)) / 1800)
from shifts s
where ds.shift_id = s.id;

alter table doctor_schedules alter column general_capacity set not null;
alter table doctor_schedules alter column specialist_capacity set not null;
alter table doctor_schedules
  add constraint ck_doctor_schedules_capacity_positive
  check (general_capacity > 0 and specialist_capacity > 0);
