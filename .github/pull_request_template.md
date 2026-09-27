## Mô tả

<!-- PR này làm gì? Liên kết issue nếu có: Closes #... -->

## Module

<!-- Module nào bị ảnh hưởng? (identity/facility/catalog/doctor/schedule/patient/booking/
reception/examination/billing/violation/corporate/notification/reporting/shared/build-ci) -->

## Checklist

- [ ] Test pass (`./gradlew test`, và `./gradlew integrationTest` nếu có đổi liên quan DB)
- [ ] Migration Flyway (nếu có) đặt tên đúng quy ước `V<timestamp>__<module>_<mô tả>.sql`
- [ ] Không log hoặc trả về response lỗi dữ liệu nhạy cảm (CCCD, tiền sử bệnh...)
- [ ] Đã cập nhật Swagger/OpenAPI nếu có đổi API (tự động, chỉ cần kiểm tra `/v3/api-docs`)
- [ ] Đã tự chạy `./gradlew spotlessApply` (hoặc để pre-commit hook tự làm)
