ALTER TABLE behandling
    ADD COLUMN IF NOT EXISTS lukket bool not null default false;
