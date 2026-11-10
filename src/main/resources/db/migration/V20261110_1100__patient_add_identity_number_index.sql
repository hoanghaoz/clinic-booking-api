-- Reception looks patients up by identity number alone; uk_patients_identity leads with
-- identity_type, so it cannot serve that query.
create index idx_patients_identity_number on patients (identity_number);
