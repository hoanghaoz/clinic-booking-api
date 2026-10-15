-- Module: company
-- FK ở tầng DB cho các tham chiếu theo DBML. Entity vẫn giữ ID kiểu Long (không @ManyToOne
-- xuyên module); FK chỉ để DB chặn ID không tồn tại và chặn xoá bản ghi cha còn được tham chiếu.

alter table company_users
  add constraint fk_company_users_user_id
  foreign key (user_id) references users (id);

alter table corporate_contracts
  add constraint fk_corporate_contracts_created_by_user_id
  foreign key (created_by_user_id) references users (id);

alter table contract_packages
  add constraint fk_contract_packages_package_version_id
  foreign key (package_version_id) references package_versions (id);

alter table contract_members
  add constraint fk_contract_members_patient_id
  foreign key (patient_id) references patients (id);

alter table contract_members
  add constraint fk_contract_members_uploaded_by_user_id
  foreign key (uploaded_by_user_id) references users (id);
