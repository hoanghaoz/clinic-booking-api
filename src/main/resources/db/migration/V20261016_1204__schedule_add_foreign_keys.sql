-- Module: schedule
-- FK ở tầng DB cho các tham chiếu theo DBML. Entity vẫn giữ ID kiểu Long (không @ManyToOne
-- xuyên module); FK chỉ để DB chặn ID không tồn tại và chặn xoá bản ghi cha còn được tham chiếu.

alter table doctor_schedules
  add constraint fk_doctor_schedules_room_id
  foreign key (room_id) references rooms (id);

alter table doctor_schedules
  add constraint fk_doctor_schedules_doctor_id_specialty_id
  foreign key (doctor_id, specialty_id) references doctor_specialities (doctor_id, specialty_id);
