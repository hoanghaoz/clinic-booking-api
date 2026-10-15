-- Module: shared
-- Mặc định updated_at = now() khi insert bằng SQL tay (seed, script). Chỉ có tác dụng lúc INSERT:
-- UPDATE bằng SQL tay sẽ KHÔNG tự đổi updated_at (cần trigger). App vẫn tự điền qua JPA auditing.
-- specialties đã có default từ trước.

alter table appointments alter column updated_at set default now();
alter table appointment_waitlists alter column updated_at set default now();
alter table clinics alter column updated_at set default now();
alter table companies alter column updated_at set default now();
alter table contract_members alter column updated_at set default now();
alter table corporate_contracts alter column updated_at set default now();
alter table doctors alter column updated_at set default now();
alter table doctor_schedules alter column updated_at set default now();
alter table doctor_schedule_slots alter column updated_at set default now();
alter table invoices alter column updated_at set default now();
alter table medical_items alter column updated_at set default now();
alter table medical_packages alter column updated_at set default now();
alter table medical_services alter column updated_at set default now();
alter table medical_visits alter column updated_at set default now();
alter table notifications alter column updated_at set default now();
alter table patient_medical_histories alter column updated_at set default now();
alter table patients alter column updated_at set default now();
alter table patient_violations alter column updated_at set default now();
alter table refresh_tokens alter column updated_at set default now();
alter table rooms alter column updated_at set default now();
alter table shifts alter column updated_at set default now();
alter table staffs alter column updated_at set default now();
alter table users alter column updated_at set default now();
