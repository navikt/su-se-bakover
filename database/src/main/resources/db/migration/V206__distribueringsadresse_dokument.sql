ALTER TABLE dokument
    ADD COLUMN IF NOT EXISTS
        distribueringsadresse jsonb default null;