ALTER TABLE dokument_distribusjon
  ADD COLUMN IF NOT EXISTS
    distribusjon_failure_count bigint not null default 0;

ALTER TABLE dokument_distribusjon
  ADD COLUMN IF NOT EXISTS
    distribusjon_last_failure_timestamp timestamptz default null;
