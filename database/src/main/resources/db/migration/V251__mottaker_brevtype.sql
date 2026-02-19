ALTER TABLE mottaker
    ADD COLUMN IF NOT EXISTS brevtype text;

UPDATE mottaker
SET brevtype = 'VEDTAK'
WHERE brevtype IS NULL;

ALTER TABLE dokument
    ADD COLUMN IF NOT EXISTS brevtype text;

-- Vedtak kunne alltid identifiseres før denne kolonnen ble innført.
UPDATE dokument
SET brevtype = 'VEDTAK'
WHERE brevtype IS NULL
  AND type = 'VEDTAK';
