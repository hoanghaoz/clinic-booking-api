-- Module: notification
-- FK ở tầng DB cho các tham chiếu theo DBML. Entity vẫn giữ ID kiểu Long (không @ManyToOne
-- xuyên module); FK chỉ để DB chặn ID không tồn tại và chặn xoá bản ghi cha còn được tham chiếu.

alter table notifications
  add constraint fk_notifications_user_id
  foreign key (user_id) references users (id);

alter table notifications
  add constraint fk_notifications_created_by_user_id
  foreign key (created_by_user_id) references users (id);

alter table notifications
  add constraint fk_notifications_patient_id
  foreign key (patient_id) references patients (id);

alter table notifications
  add constraint fk_notifications_appointment_id
  foreign key (appointment_id) references appointments (id);

alter table notifications
  add constraint fk_notifications_waitlist_id
  foreign key (waitlist_id) references appointment_waitlists (id);

alter table notifications
  add constraint fk_notifications_medical_visit_id
  foreign key (medical_visit_id) references medical_visits (id);

alter table notifications
  add constraint fk_notifications_invoice_id
  foreign key (invoice_id) references invoices (id);
