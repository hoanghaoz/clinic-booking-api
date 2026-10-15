-- Module: employee
-- FK ở tầng DB cho các tham chiếu theo DBML. Entity vẫn giữ ID kiểu Long (không @ManyToOne
-- xuyên module); FK chỉ để DB chặn ID không tồn tại và chặn xoá bản ghi cha còn được tham chiếu.

alter table staffs
  add constraint fk_staffs_user_id
  foreign key (user_id) references users (id);

alter table clinic_staffs
  add constraint fk_clinic_staffs_clinic_id
  foreign key (clinic_id) references clinics (id);

create index idx_clinic_staffs_staff_id on clinic_staffs (staff_id);
