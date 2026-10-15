-- Module: doctor
-- FK ở tầng DB cho các tham chiếu theo DBML. Entity vẫn giữ ID kiểu Long (không @ManyToOne
-- xuyên module); FK chỉ để DB chặn ID không tồn tại và chặn xoá bản ghi cha còn được tham chiếu.

alter table doctors
  add constraint fk_doctors_user_id
  foreign key (user_id) references users (id);

create index idx_doctor_specialities_specialty_id on doctor_specialities (specialty_id);
