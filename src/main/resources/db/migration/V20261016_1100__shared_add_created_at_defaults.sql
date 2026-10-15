-- Module: shared
-- Mặc định created_at = now() để insert bằng SQL tay (seed, script) không phải tự ghi giá trị.
-- App vẫn tự điền qua JPA auditing / @CreationTimestamp. specialties đã có default từ trước.

alter table appointment_histories alter column created_at set default now();
alter table appointments alter column created_at set default now();
alter table appointment_waitlists alter column created_at set default now();
alter table clinics alter column created_at set default now();
alter table companies alter column created_at set default now();
alter table company_users alter column created_at set default now();
alter table contract_members alter column created_at set default now();
alter table contract_packages alter column created_at set default now();
alter table corporate_contracts alter column created_at set default now();
alter table doctors alter column created_at set default now();
alter table doctor_schedules alter column created_at set default now();
alter table doctor_schedule_slots alter column created_at set default now();
alter table doctor_specialities alter column created_at set default now();
alter table invoice_items alter column created_at set default now();
alter table invoices alter column created_at set default now();
alter table medical_items alter column created_at set default now();
alter table medical_packages alter column created_at set default now();
alter table medical_service_items alter column created_at set default now();
alter table medical_services alter column created_at set default now();
alter table medical_visits alter column created_at set default now();
alter table notifications alter column created_at set default now();
alter table package_specialities alter column created_at set default now();
alter table package_versions alter column created_at set default now();
alter table patients alter column created_at set default now();
alter table patient_violations alter column created_at set default now();
alter table payments alter column created_at set default now();
alter table refresh_tokens alter column created_at set default now();
alter table refunds alter column created_at set default now();
alter table rooms alter column created_at set default now();
alter table shifts alter column created_at set default now();
alter table staffs alter column created_at set default now();
alter table users alter column created_at set default now();
