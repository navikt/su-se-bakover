ALTER TABLE dokument
    ADD COLUMN IF NOT EXISTS brevtype text;

UPDATE dokument
SET brevtype = dokument_formaal
WHERE brevtype IS NULL
  AND dokument_formaal IS NOT NULL;
