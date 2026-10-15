-- Module: payment
-- FK ở tầng DB cho các tham chiếu theo DBML. Entity vẫn giữ ID kiểu Long (không @ManyToOne
-- xuyên module); FK chỉ để DB chặn ID không tồn tại và chặn xoá bản ghi cha còn được tham chiếu.

alter table invoices
  add constraint fk_invoices_appointment_id
  foreign key (appointment_id) references appointments (id);

alter table invoices
  add constraint fk_invoices_company_id
  foreign key (company_id) references companies (id);

alter table invoices
  add constraint fk_invoices_contract_id
  foreign key (contract_id) references corporate_contracts (id);

alter table invoices
  add constraint fk_invoices_created_by_staff_id
  foreign key (created_by_staff_id) references staffs (id);

alter table invoice_items
  add constraint fk_invoice_items_medical_order_id
  foreign key (medical_order_id) references medical_orders (id);

alter table payments
  add constraint fk_payments_received_by_staff_id
  foreign key (received_by_staff_id) references staffs (id);

alter table refunds
  add constraint fk_refunds_cancellation_history_id
  foreign key (cancellation_history_id) references appointment_histories (id);

alter table refunds
  add constraint fk_refunds_processed_by_staff_id
  foreign key (processed_by_staff_id) references staffs (id);

create index idx_invoices_company_id on invoices (company_id);

create index idx_invoices_contract_id on invoices (contract_id);
