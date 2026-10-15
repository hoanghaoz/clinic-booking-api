-- Module: violation
-- FK ở tầng DB cho các tham chiếu theo DBML. Entity vẫn giữ ID kiểu Long (không @ManyToOne
-- xuyên module); FK chỉ để DB chặn ID không tồn tại và chặn xoá bản ghi cha còn được tham chiếu.

alter table patient_violations
  add constraint fk_patient_violations_patient_id
  foreign key (patient_id) references patients (id);

alter table patient_violations
  add constraint fk_patient_violations_appointment_id
  foreign key (appointment_id) references appointments (id);

alter table patient_violations
  add constraint fk_patient_violations_medical_order_id
  foreign key (medical_order_id) references medical_orders (id);

alter table patient_violations
  add constraint fk_patient_violations_recorded_by_staff_id
  foreign key (recorded_by_staff_id) references staffs (id);

create index idx_patient_violations_appointment_id on patient_violations (appointment_id);
