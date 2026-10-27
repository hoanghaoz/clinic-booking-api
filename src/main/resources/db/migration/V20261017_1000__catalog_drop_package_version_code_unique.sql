-- Module: catalog
-- package_versions.code là snapshot mã gói tại thời điểm tạo version. Mã gói có thể đổi, nhưng
-- các version của cùng một gói thường giữ nguyên mã, nên không được unique toàn bảng.
-- Tính duy nhất của version đã do uk_package_versions_package_version (package_id, version) đảm bảo.

alter table package_versions drop constraint package_versions_code_key;
