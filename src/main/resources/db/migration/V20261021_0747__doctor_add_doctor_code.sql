-- Module: doctor
-- Mã bác sĩ (RES-04 / UC-11: "Mã bác sĩ ... phải duy nhất"). Bảng doctors ban đầu chưa có cột này.
-- Thêm nullable -> điền mã tạm cho dòng cũ (nếu DB local đã có dữ liệu) -> mới set not null.

alter table doctors add column code varchar(50);

update doctors set code = 'BS' || id where code is null;

alter table doctors alter column code set not null;

alter table doctors add constraint uq_doctors_code unique (code);
