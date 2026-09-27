-- Module: doctor
-- Tạo bảng Chuyên khoa (ChuyenKhoa) — xem docs/de-tai.md mục B.
--
-- Quy ước đặt tên file Flyway của dự án: V<timestamp yyyyMMdd_HHmm>__<module>_<mô tả>.sql
-- Dùng timestamp thay vì số tăng dần (V1, V2, V3...) để nhiều thành viên cùng tạo migration
-- song song ở các nhánh khác nhau mà không bị đụng version khi merge (xem README).
create table specialties (
    id          bigint generated always as identity primary key,
    code        varchar(50)  not null unique,
    name        varchar(200) not null,
    description text,
    active      boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

comment on table specialties is 'Chuyên khoa (ChuyenKhoa) — module doctor';
