ALTER TABLE mottaker
    ADD COLUMN IF NOT EXISTS brevtype text;

UPDATE mottaker
SET brevtype = 'VEDTAK'
WHERE brevtype IS NULL;

UPDATE mottaker
SET brevtype = 'VEDTAK'
WHERE upper(trim(brevtype)) = 'VEDTAKSBREV';

ALTER TABLE mottaker
    ALTER COLUMN brevtype SET NOT NULL,
    ALTER COLUMN brevtype DROP DEFAULT;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'mottaker_brevtype_check'
    ) THEN
        ALTER TABLE mottaker
            ADD CONSTRAINT mottaker_brevtype_check
                CHECK (brevtype IN ('VEDTAK', 'FORHANDSVARSEL'));
    END IF;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mottaker_referanse_type_id_brevtype
    ON mottaker (referanse_type, referanse_id, brevtype);

ALTER TABLE dokument
    ADD COLUMN IF NOT EXISTS brevtype text;

UPDATE dokument
SET brevtype = 'VEDTAK'
WHERE brevtype IS NULL
  AND type = 'VEDTAK';

UPDATE dokument
SET brevtype = 'ANNET'
WHERE brevtype IS NULL
  AND type = 'INFORMASJON_ANNET';
