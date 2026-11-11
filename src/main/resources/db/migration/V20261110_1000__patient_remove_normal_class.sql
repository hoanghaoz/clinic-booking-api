-- NORMAL duplicated PERSONAL; the requirements define only personal, VIP and company patients.
update patients set class = 'PERSONAL' where class = 'NORMAL';

alter table patients drop constraint patients_class_check;
alter table patients add constraint patients_class_check
  check (class in ('PERSONAL','VIP','COMPANY'));
