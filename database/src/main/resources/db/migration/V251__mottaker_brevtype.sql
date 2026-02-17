ALTER TABLE mottaker
    ADD COLUMN IF NOT EXISTS brevtype text;

UPDATE mottaker
SET brevtype = 'VEDTAKSBREV'
WHERE brevtype IS NULL;

ALTER TABLE mottaker
    ALTER COLUMN brevtype SET DEFAULT 'VEDTAKSBREV',
    ALTER COLUMN brevtype SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mottaker_referanse_type_id_brevtype
    ON mottaker (referanse_type, referanse_id, brevtype);
