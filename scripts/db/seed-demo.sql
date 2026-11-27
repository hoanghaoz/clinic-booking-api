-- Demo data for LOCAL development only. Never run this against a shared or production database:
-- every account below uses the same published password.
--
-- Usage (from the repository root, with the compose Postgres running and migrations applied):
--   docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < scripts/db/seed-demo.sql
--
-- The script only inserts, and is safe to run again: existing rows are left untouched. Doctor
-- schedules cover today through today + 30 days, so run it again later to extend the window.

\set ON_ERROR_STOP on
\set demo_password 'Demo@12345'

begin;

create extension if not exists pgcrypto;

-- Accounts -----------------------------------------------------------------------------------

insert into users (public_id, username, email, password_hashed, role, status)
select gen_random_uuid(), v.username, v.username || '@example.com',
       crypt(:'demo_password', gen_salt('bf', 10)), v.role, 'ACTIVE'
from (values
  ('demo_admin', 'ADMIN'),
  ('demo_bs01', 'DOCTOR'), ('demo_bs02', 'DOCTOR'), ('demo_bs03', 'DOCTOR'),
  ('demo_bs04', 'DOCTOR'), ('demo_bs05', 'DOCTOR'), ('demo_bs06', 'DOCTOR'),
  ('demo_bs07', 'DOCTOR'), ('demo_bs08', 'DOCTOR'), ('demo_bs09', 'DOCTOR'),
  ('demo_bs10', 'DOCTOR'),
  ('demo_bn01', 'PATIENT'), ('demo_bn02', 'PATIENT'), ('demo_bn03', 'PATIENT'),
  ('demo_bn04', 'PATIENT'), ('demo_bn05', 'PATIENT'), ('demo_bn06', 'PATIENT')
) as v(username, role)
on conflict do nothing;

-- Clinics and rooms --------------------------------------------------------------------------

insert into clinics (public_id, code, name, address, phone_number, email, description, status)
select gen_random_uuid(), v.code, v.name, v.address, v.phone, v.email, v.description, 'OPENING'
from (values
  ('AT-Q1', 'Phòng khám Đa khoa An Tâm - Quận 1',
   '12 Nguyễn Thị Minh Khai, Quận 1, TP. Hồ Chí Minh', '028 3800 0001', 'q1@example.com',
   'Cơ sở trung tâm, khám tổng quát và chuyên khoa'),
  ('AT-TD', 'Phòng khám Đa khoa An Tâm - Thủ Đức',
   '45 Võ Văn Ngân, TP. Thủ Đức, TP. Hồ Chí Minh', '028 3800 0002', 'thuduc@example.com',
   'Cơ sở khu Đông, gần làng đại học'),
  ('AT-Q7', 'Phòng khám Đa khoa An Tâm - Quận 7',
   '88 Nguyễn Văn Linh, Quận 7, TP. Hồ Chí Minh', '028 3800 0003', 'q7@example.com',
   'Cơ sở khu Nam')
) as v(code, name, address, phone, email, description)
on conflict do nothing;

insert into rooms (public_id, clinic_id, code, name, status)
select gen_random_uuid(), c.id, v.code, v.name, 'OPENING'
from clinics c
cross join (values
  ('P101', 'Phòng khám 101'), ('P102', 'Phòng khám 102'), ('P103', 'Phòng khám 103')
) as v(code, name)
where c.code in ('AT-Q1', 'AT-TD', 'AT-Q7')
on conflict do nothing;

-- Specialties and doctors --------------------------------------------------------------------

insert into specialties (code, name, description)
values
  ('NOI', 'Nội tổng quát', 'Khám và điều trị các bệnh nội khoa thường gặp'),
  ('NHI', 'Nhi khoa', 'Chăm sóc sức khỏe trẻ em từ sơ sinh đến 15 tuổi'),
  ('TIMMACH', 'Tim mạch', 'Tăng huyết áp, bệnh mạch vành, rối loạn nhịp tim'),
  ('DALIEU', 'Da liễu', 'Bệnh lý về da, tóc và móng'),
  ('TMH', 'Tai Mũi Họng', 'Bệnh lý tai, mũi, xoang và họng'),
  ('SANPHU', 'Sản phụ khoa', 'Chăm sóc sức khỏe phụ nữ và thai sản'),
  ('CXK', 'Cơ xương khớp', 'Thoái hóa khớp, viêm khớp, đau cột sống'),
  ('MAT', 'Mắt', 'Khám thị lực và các bệnh lý về mắt')
on conflict do nothing;

insert into doctors (public_id, user_id, code, name, degree, experience, status)
select gen_random_uuid(), u.id, v.code, v.name, v.degree, v.experience, 'WORKING'
from (values
  ('demo_bs01', 'BS001', 'Nguyễn Văn An', 'Bác sĩ Chuyên khoa II', '18 năm khám nội tổng quát'),
  ('demo_bs02', 'BS002', 'Trần Thị Bích Ngọc', 'Thạc sĩ, Bác sĩ', '10 năm công tác tại khoa Nhi'),
  ('demo_bs03', 'BS003', 'Lê Hoàng Minh', 'Tiến sĩ, Bác sĩ', '15 năm điều trị tim mạch'),
  ('demo_bs04', 'BS004', 'Phạm Thu Hà', 'Bác sĩ Chuyên khoa I', '8 năm khám da liễu'),
  ('demo_bs05', 'BS005', 'Võ Quốc Khánh', 'Bác sĩ Chuyên khoa I', '12 năm khám tai mũi họng'),
  ('demo_bs06', 'BS006', 'Đặng Mỹ Linh', 'Thạc sĩ, Bác sĩ', '11 năm công tác sản phụ khoa'),
  ('demo_bs07', 'BS007', 'Bùi Đức Thắng', 'Bác sĩ Chuyên khoa II', '20 năm điều trị cơ xương khớp'),
  ('demo_bs08', 'BS008', 'Hồ Ngọc Anh', 'Bác sĩ', '6 năm khám mắt'),
  ('demo_bs09', 'BS009', 'Ngô Thanh Tùng', 'Bác sĩ Chuyên khoa I', '9 năm khám nội và tim mạch'),
  ('demo_bs10', 'BS010', 'Dương Khánh Vy', 'Bác sĩ', '5 năm khám nội và nhi')
) as v(username, code, name, degree, experience)
join users u on u.username = v.username
on conflict do nothing;

insert into doctor_specialities (doctor_id, specialty_id)
select d.id, s.id
from (values
  ('BS001', 'NOI'), ('BS002', 'NHI'), ('BS003', 'TIMMACH'), ('BS003', 'NOI'),
  ('BS004', 'DALIEU'), ('BS005', 'TMH'), ('BS006', 'SANPHU'), ('BS007', 'CXK'),
  ('BS008', 'MAT'), ('BS009', 'NOI'), ('BS009', 'TIMMACH'), ('BS010', 'NOI'), ('BS010', 'NHI')
) as v(doctor_code, specialty_code)
join doctors d on d.code = v.doctor_code
join specialties s on s.code = v.specialty_code
on conflict do nothing;

-- Medical items, packages and standalone services --------------------------------------------

insert into medical_items (public_id, name, type, price, description, instruction)
select gen_random_uuid(), v.name, v.type, v.price, v.description, v.instruction
from (values
  ('Khám nội tổng quát', 'CLINICAL', 150000, 'Khám lâm sàng, đo sinh hiệu', null),
  ('Khám nhi', 'CLINICAL', 150000, 'Khám lâm sàng và đánh giá tăng trưởng',
   'Mang theo sổ tiêm chủng'),
  ('Khám phụ khoa', 'CLINICAL', 200000, 'Khám phụ khoa định kỳ',
   'Không đến khám trong kỳ kinh nguyệt'),
  ('Tổng phân tích tế bào máu', 'LAB', 90000, 'Công thức máu', null),
  ('Đường huyết lúc đói', 'LAB', 40000, 'Định lượng glucose máu',
   'Nhịn ăn ít nhất 8 giờ'),
  ('Bộ mỡ máu', 'LAB', 160000, 'Cholesterol, HDL, LDL, Triglyceride', 'Nhịn ăn ít nhất 8 giờ'),
  ('Tổng phân tích nước tiểu', 'LAB', 50000, 'Xét nghiệm nước tiểu', null),
  ('Siêu âm bụng tổng quát', 'IMAGING', 180000, 'Siêu âm các cơ quan trong ổ bụng',
   'Uống nhiều nước và nhịn tiểu trước khi siêu âm'),
  ('X-quang ngực thẳng', 'IMAGING', 120000, 'Chụp X-quang tim phổi',
   'Báo trước nếu đang hoặc nghi ngờ mang thai'),
  ('Điện tâm đồ', 'OTHER', 80000, 'Ghi điện tim 12 chuyển đạo', null),
  ('Siêu âm tim', 'IMAGING', 300000, 'Siêu âm Doppler tim', null),
  ('Tiêm vắc xin cúm', 'VACCINATION', 350000, 'Một liều vắc xin cúm mùa',
   'Báo bác sĩ nếu đang sốt hoặc có tiền sử dị ứng')
) as v(name, type, price, description, instruction)
on conflict do nothing;

create temporary table demo_packages (
  code text, name text, class text, target text, min_age int, max_age int,
  minutes int, price numeric, description text, instruction text, specialties text[], items text[]
) on commit drop;

insert into demo_packages values
  ('GOI-TQ-CB', 'Khám sức khỏe tổng quát cơ bản', 'STANDARD', 'BOTH', 18, null, 20, 550000,
   'Kiểm tra sức khỏe định kỳ cho người trưởng thành', 'Nhịn ăn ít nhất 8 giờ trước khi khám',
   array['NOI'],
   array['Khám nội tổng quát', 'Tổng phân tích tế bào máu', 'Đường huyết lúc đói',
         'Tổng phân tích nước tiểu']),
  ('GOI-TQ-NC', 'Khám sức khỏe tổng quát nâng cao', 'ADVANCED', 'BOTH', 18, null, 30, 1250000,
   'Gói tổng quát kèm chẩn đoán hình ảnh',
   'Nhịn ăn ít nhất 8 giờ, uống nước và nhịn tiểu trước khi siêu âm',
   array['NOI'],
   array['Khám nội tổng quát', 'Tổng phân tích tế bào máu', 'Đường huyết lúc đói', 'Bộ mỡ máu',
         'Tổng phân tích nước tiểu', 'Siêu âm bụng tổng quát', 'X-quang ngực thẳng',
         'Điện tâm đồ']),
  ('GOI-TIM', 'Tầm soát bệnh lý tim mạch', 'SPECIALIZED', 'BOTH', 30, null, 30, 850000,
   'Phát hiện sớm nguy cơ tim mạch', 'Nhịn ăn ít nhất 8 giờ trước khi khám',
   array['TIMMACH'],
   array['Bộ mỡ máu', 'Đường huyết lúc đói', 'Điện tâm đồ', 'Siêu âm tim']),
  ('GOI-PK', 'Khám phụ khoa định kỳ', 'SPECIALIZED', 'FEMALE', 18, null, 30, 450000,
   'Chăm sóc sức khỏe phụ nữ', 'Không đến khám trong kỳ kinh nguyệt',
   array['SANPHU'],
   array['Khám phụ khoa', 'Tổng phân tích nước tiểu', 'Siêu âm bụng tổng quát']),
  ('GOI-NHI', 'Khám sức khỏe định kỳ cho trẻ', 'STANDARD', 'BOTH', 0, 15, 20, 350000,
   'Theo dõi tăng trưởng và phát triển của trẻ', 'Mang theo sổ tiêm chủng',
   array['NHI'],
   array['Khám nhi', 'Tổng phân tích tế bào máu']);

insert into medical_packages (public_id, code, name)
select gen_random_uuid(), p.code, p.name from demo_packages p
on conflict do nothing;

insert into package_versions (package_id, version, code, name, class, target, min_age, max_age,
                              consultation_minutes, unit_price, description, instruction, status,
                              effective_from)
select m.id, 'v1.0', p.code, p.name, p.class, p.target, p.min_age, p.max_age, p.minutes, p.price,
       p.description, p.instruction, 'OPENING', now()
from demo_packages p
join medical_packages m on m.code = p.code
-- A package that already has any version was edited through the API; leave it alone.
where not exists (select 1 from package_versions v where v.package_id = m.id);

insert into package_version_items (package_version_id, item_id, "position", quantity,
                                   item_name_snapshot, instruction_snapshot)
select v.id, i.id, x.position, 1, i.name, i.instruction
from demo_packages p
join medical_packages m on m.code = p.code
join package_versions v on v.package_id = m.id and v.version = 'v1.0'
cross join lateral unnest(p.items) with ordinality as x(item_name, position)
join medical_items i on i.name = x.item_name
on conflict do nothing;

insert into package_specialities (package_id, specialty_id)
select m.id, s.id
from demo_packages p
join medical_packages m on m.code = p.code
cross join lateral unnest(p.specialties) as x(specialty_code)
join specialties s on s.code = x.specialty_code
on conflict do nothing;

create temporary table demo_services (
  name text, minutes int, price numeric, target text, description text, instruction text,
  item text
) on commit drop;

insert into demo_services values
  ('Khám nội theo yêu cầu', 20, 200000, 'BOTH', 'Khám nội khoa một lần', null,
   'Khám nội tổng quát'),
  ('Đo điện tim', 20, 100000, 'BOTH', 'Đo và đọc kết quả điện tâm đồ', null, 'Điện tâm đồ'),
  ('Siêu âm bụng', 30, 200000, 'BOTH', 'Siêu âm bụng tổng quát',
   'Uống nhiều nước và nhịn tiểu trước khi siêu âm', 'Siêu âm bụng tổng quát'),
  ('Tiêm phòng cúm', 20, 380000, 'BOTH', 'Khám sàng lọc và tiêm vắc xin cúm',
   'Báo bác sĩ nếu đang sốt hoặc có tiền sử dị ứng', 'Tiêm vắc xin cúm');

insert into medical_services (public_id, name, duration_minutes, price, target, description,
                              instruction)
select gen_random_uuid(), s.name, s.minutes, s.price, s.target, s.description, s.instruction
from demo_services s
where not exists (select 1 from medical_services m where m.name = s.name);

insert into medical_service_items (service_id, item_id)
select m.id, i.id
from demo_services s
join medical_services m on m.name = s.name
join medical_items i on i.name = s.item
on conflict do nothing;

-- Patient profiles ---------------------------------------------------------------------------
-- Cards are valid for one year, except BN06 which expires soon (to exercise the reminder flow).

insert into patients (public_id, user_id, full_name, date_of_birth, gender, phone, contact_email,
                      address, identity_type, identity_number, identity_issuer, class,
                      card_number, card_activated_at, card_expires_at)
select gen_random_uuid(), u.id, v.full_name, v.date_of_birth::date, v.gender, v.phone, u.email,
       v.address, 'CCCD', v.identity_number, 'Cục Cảnh sát QLHC về TTXH', v.class,
       v.card_number, now(), now() + v.card_valid_for::interval
from (values
  ('demo_bn01', 'Nguyễn Minh Quân', '1998-03-12', 'MALE', '0900 000 001',
   '5 Lê Lợi, Quận 1, TP. Hồ Chí Minh', '000000000001', 'PERSONAL', 'BN2026-DEMO0001', '1 year'),
  ('demo_bn02', 'Trần Thảo Nhi', '2001-07-25', 'FEMALE', '0900 000 002',
   '20 Kha Vạn Cân, TP. Thủ Đức, TP. Hồ Chí Minh', '000000000002', 'PERSONAL',
   'BN2026-DEMO0002', '1 year'),
  ('demo_bn03', 'Lê Văn Phúc', '1975-11-02', 'MALE', '0900 000 003',
   '101 Huỳnh Tấn Phát, Quận 7, TP. Hồ Chí Minh', '000000000003', 'VIP',
   'BN2026-DEMO0003', '1 year'),
  ('demo_bn04', 'Phạm Thị Hồng', '1966-01-18', 'FEMALE', '0900 000 004',
   '33 Điện Biên Phủ, Bình Thạnh, TP. Hồ Chí Minh', '000000000004', 'PERSONAL',
   'BN2026-DEMO0004', '1 year'),
  ('demo_bn05', 'Hoàng Gia Bảo', '2017-09-05', 'MALE', '0900 000 005',
   '7 Phạm Văn Đồng, TP. Thủ Đức, TP. Hồ Chí Minh', '000000000005', 'PERSONAL',
   'BN2026-DEMO0005', '1 year'),
  ('demo_bn06', 'Đỗ Khánh Linh', '1990-05-30', 'FEMALE', '0900 000 006',
   '64 Cách Mạng Tháng Tám, Quận 3, TP. Hồ Chí Minh', '000000000006', 'PERSONAL',
   'BN2026-DEMO0006', '20 days')
) as v(username, full_name, date_of_birth, gender, phone, address, identity_number, class,
       card_number, card_valid_for)
join users u on u.username = v.username
on conflict do nothing;

-- Doctor schedules ---------------------------------------------------------------------------
-- Monday to Saturday, morning and afternoon, in all 9 demo rooms. Each day 9 of the 10 doctors
-- work a full day in one room. The assignment depends only on the date, so reruns agree.

with demo_rooms as (
  select r.id as room_id, row_number() over (order by c.code, r.code) - 1 as room_no
  from rooms r
  join clinics c on c.id = r.clinic_id
  where c.code in ('AT-Q1', 'AT-TD', 'AT-Q7') and r.code in ('P101', 'P102', 'P103')
    and c.status = 'OPENING' and r.status = 'OPENING'
),
demo_doctors as (
  select d.id as doctor_id, d.status,
         substring(d.code from 3)::int - 1 as doctor_no,
         array(select ds.specialty_id from doctor_specialities ds
               where ds.doctor_id = d.id order by ds.specialty_id) as specialty_ids
  from doctors d
  join users u on u.id = d.user_id
  where u.username like 'demo\_bs%' and d.code ~ '^BS[0-9]{3}$'
),
days as (
  select day::date as work_date, day::date - date '2026-01-01' as day_no
  from generate_series(
         (now() at time zone 'Asia/Ho_Chi_Minh')::date,
         (now() at time zone 'Asia/Ho_Chi_Minh')::date + 30,
         interval '1 day') as day
  where extract(isodow from day) < 7
)
insert into doctor_schedules (public_id, doctor_id, specialty_id, room_id, shift_id, work_date,
                              start_at, end_at, general_capacity, specialist_capacity, status)
select gen_random_uuid(), d.doctor_id,
       -- Doctors with two specialties switch between morning and afternoon, with half of them
       -- starting on the other one, so every specialty is available on every working day.
       d.specialty_ids[1 + (d.doctor_no / 6 + case s.code when 'MORNING' then 0 else 1 end)
                           % cardinality(d.specialty_ids)],
       r.room_id, s.id, days.work_date,
       (days.work_date + s.start_time) at time zone 'Asia/Ho_Chi_Minh',
       (days.work_date + s.end_time) at time zone 'Asia/Ho_Chi_Minh',
       8, 4, 'OPEN'
from days
cross join demo_rooms r
join demo_doctors d on d.doctor_no = (r.room_no + days.day_no) % 10
join shifts s on s.code in ('MORNING', 'AFTERNOON')
where d.status = 'WORKING'
  and cardinality(d.specialty_ids) > 0
  and not exists (select 1 from doctor_schedules x
                  where x.doctor_id = d.doctor_id and x.work_date = days.work_date
                    and x.shift_id = s.id)
-- Skips shifts that overlap a schedule someone created by hand.
on conflict do nothing;

-- Ten-minute slots, matching what the schedule API generates.
insert into doctor_schedule_slots (schedule_id, start_at, end_at)
select s.id, slot_start, slot_start + interval '10 minutes'
from doctor_schedules s
join doctors d on d.id = s.doctor_id
join users u on u.id = d.user_id
cross join lateral generate_series(
  s.start_at, s.end_at - interval '10 minutes', interval '10 minutes') as slot_start
where u.username like 'demo\_bs%'
  and not exists (select 1 from doctor_schedule_slots x where x.schedule_id = s.id)
on conflict do nothing;

commit;

-- Summary of the demo rows.
select 'accounts' as item, count(*) from users where username like 'demo\_%'
union all select 'clinics', count(*) from clinics where code in ('AT-Q1', 'AT-TD', 'AT-Q7')
union all select 'rooms', count(*) from rooms r join clinics c on c.id = r.clinic_id
  where c.code in ('AT-Q1', 'AT-TD', 'AT-Q7')
union all select 'doctors', count(*) from doctors d join users u on u.id = d.user_id
  where u.username like 'demo\_bs%'
union all select 'patients', count(*) from patients p join users u on u.id = p.user_id
  where u.username like 'demo\_bn%'
union all select 'packages', count(*) from medical_packages
  where code in ('GOI-TQ-CB', 'GOI-TQ-NC', 'GOI-TIM', 'GOI-PK', 'GOI-NHI')
union all select 'schedules', count(*) from doctor_schedules s join doctors d on d.id = s.doctor_id
  join users u on u.id = d.user_id where u.username like 'demo\_bs%'
union all select 'slots', count(*) from doctor_schedule_slots x
  join doctor_schedules s on s.id = x.schedule_id join doctors d on d.id = s.doctor_id
  join users u on u.id = d.user_id where u.username like 'demo\_bs%';
