-- identity_type was free text; restrict it to the IdentityType enum.
update patients set identity_type = upper(trim(identity_type));

alter table patients add constraint patients_identity_type_check
  check (identity_type in ('CCCD','CMND','PASSPORT'));
