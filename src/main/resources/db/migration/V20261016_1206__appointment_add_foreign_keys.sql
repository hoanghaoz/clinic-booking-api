-- Module: appointment
-- FK ở tầng DB cho các tham chiếu theo DBML. Entity vẫn giữ ID kiểu Long (không @ManyToOne
-- xuyên module); FK chỉ để DB chặn ID không tồn tại và chặn xoá bản ghi cha còn được tham chiếu.

alter table appointments
  add constraint fk_appointments_patient_id
  foreign key (patient_id) references patients (id);

alter table appointments
  add constraint fk_appointments_contract_member_id
  foreign key (contract_member_id) references contract_members (id);

alter table appointments
  add constraint fk_appointments_schedule_id
  foreign key (schedule_id) references doctor_schedules (id);

alter table appointments
  add constraint fk_appointments_package_version_id
  foreign key (package_version_id) references package_versions (id);

alter table appointments
  add constraint fk_appointments_service_id
  foreign key (service_id) references medical_services (id);

alter table appointments
  add constraint fk_appointments_makeup_order_id
  foreign key (makeup_order_id) references medical_orders (id);

alter table appointments
  add constraint fk_appointments_followup_visit_id
  foreign key (followup_visit_id) references medical_visits (id);

alter table appointments
  add constraint fk_appointments_created_by_staff_id
  foreign key (created_by_staff_id) references staffs (id);

alter table appointment_slots
  add constraint fk_appointment_slots_slot_id
  foreign key (slot_id) references doctor_schedule_slots (id);

alter table appointment_histories
  add constraint fk_appointment_histories_actor_patient_id
  foreign key (actor_patient_id) references patients (id);

alter table appointment_histories
  add constraint fk_appointment_histories_actor_staff_id
  foreign key (actor_staff_id) references staffs (id);

alter table appointment_histories
  add constraint fk_appointment_histories_old_schedule_id
  foreign key (old_schedule_id) references doctor_schedules (id);

alter table appointment_histories
  add constraint fk_appointment_histories_new_schedule_id
  foreign key (new_schedule_id) references doctor_schedules (id);

alter table appointment_waitlists
  add constraint fk_appointment_waitlists_patient_id
  foreign key (patient_id) references patients (id);

alter table appointment_waitlists
  add constraint fk_appointment_waitlists_schedule_id
  foreign key (schedule_id) references doctor_schedules (id);

alter table appointment_waitlists
  add constraint fk_appointment_waitlists_package_version_id
  foreign key (package_version_id) references package_versions (id);

alter table appointment_waitlists
  add constraint fk_appointment_waitlists_service_id
  foreign key (service_id) references medical_services (id);

alter table appointment_waitlists
  add constraint fk_appointment_waitlists_offered_appointment_id
  foreign key (offered_appointment_id) references appointments (id);

create index idx_appointments_contract_member_id on appointments (contract_member_id);

create index idx_appointment_waitlists_patient_id on appointment_waitlists (patient_id);
