-- Module: examination
-- FK ở tầng DB cho các tham chiếu theo DBML. Entity vẫn giữ ID kiểu Long (không @ManyToOne
-- xuyên module); FK chỉ để DB chặn ID không tồn tại và chặn xoá bản ghi cha còn được tham chiếu.

alter table medical_visits
  add constraint fk_medical_visits_patient_id
  foreign key (patient_id) references patients (id);

alter table medical_visits
  add constraint fk_medical_visits_appointment_id
  foreign key (appointment_id) references appointments (id);

alter table medical_visits
  add constraint fk_medical_visits_received_by_staff_id
  foreign key (received_by_staff_id) references staffs (id);

alter table medical_visits
  add constraint fk_medical_visits_primary_doctor_id
  foreign key (primary_doctor_id) references doctors (id);

alter table medical_visits
  add constraint fk_medical_visits_prescribed_by_doctor_id
  foreign key (prescribed_by_doctor_id) references doctors (id);

alter table medical_orders
  add constraint fk_medical_orders_item_id
  foreign key (item_id) references medical_items (id);

alter table medical_orders
  add constraint fk_medical_orders_performed_visit_id
  foreign key (performed_visit_id) references medical_visits (id);

alter table medical_orders
  add constraint fk_medical_orders_room_id
  foreign key (room_id) references rooms (id);

alter table medical_orders
  add constraint fk_medical_orders_ordered_by_doctor_id
  foreign key (ordered_by_doctor_id) references doctors (id);

alter table medical_orders
  add constraint fk_medical_orders_performed_by_doctor_id
  foreign key (performed_by_doctor_id) references doctors (id);

alter table medical_orders
  add constraint fk_medical_orders_verified_by_doctor_id
  foreign key (verified_by_doctor_id) references doctors (id);

alter table medical_orders
  add constraint fk_medical_orders_performed_by_staff_id
  foreign key (performed_by_staff_id) references staffs (id);

alter table medical_orders
  add constraint fk_medical_orders_verified_by_staff_id
  foreign key (verified_by_staff_id) references staffs (id);

alter table medical_orders
  add constraint fk_medical_orders_package_version_id_item_id
  foreign key (package_version_id, item_id) references package_version_items (package_version_id, item_id);

alter table medical_orders
  add constraint fk_medical_orders_service_id_item_id
  foreign key (service_id, item_id) references medical_service_items (service_id, item_id);

create index idx_medical_orders_item_id on medical_orders (item_id);
