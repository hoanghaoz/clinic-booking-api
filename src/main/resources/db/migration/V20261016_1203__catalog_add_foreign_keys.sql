-- Module: catalog
-- FK ở tầng DB cho các tham chiếu theo DBML. Entity vẫn giữ ID kiểu Long (không @ManyToOne
-- xuyên module); FK chỉ để DB chặn ID không tồn tại và chặn xoá bản ghi cha còn được tham chiếu.

alter table package_specialities
  add constraint fk_package_specialities_specialty_id
  foreign key (specialty_id) references specialties (id);

create index idx_package_specialities_specialty_id on package_specialities (specialty_id);

create index idx_package_version_items_package_version_id on package_version_items (package_version_id);

create index idx_medical_service_items_service_id on medical_service_items (service_id);
