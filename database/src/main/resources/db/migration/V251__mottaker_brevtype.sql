ALTER TABLE mottaker
    ADD COLUMN IF NOT EXISTS brevtype text;

UPDATE mottaker
SET brevtype = 'VEDTAK'
WHERE brevtype IS NULL;
