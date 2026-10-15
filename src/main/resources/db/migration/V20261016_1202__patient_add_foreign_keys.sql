-- Module: patient
-- FK ở tầng DB cho các tham chiếu theo DBML. Entity vẫn giữ ID kiểu Long (không @ManyToOne
-- xuyên module); FK chỉ để DB chặn ID không tồn tại và chặn xoá bản ghi cha còn được tham chiếu.

alter table patients
  add constraint fk_patients_user_id
  foreign key (user_id) references users (id);

alter table patient_medical_histories
  add constraint fk_patient_medical_histories_recorded_by_staff_id
  foreign key (recorded_by_staff_id) references staffs (id);

alter table patient_medical_histories
  add constraint fk_patient_medical_histories_recorded_by_doctor_id
  foreign key (recorded_by_doctor_id) references doctors (id);
