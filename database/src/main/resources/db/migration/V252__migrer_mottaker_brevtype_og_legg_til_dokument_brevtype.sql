UPDATE mottaker
SET brevtype = 'VEDTAK'
WHERE brevtype IS NULL
   OR brevtype = 'VEDTAKSBREV';

-- Nye verdier skal settes eksplisitt av applikasjonen.
ALTER TABLE mottaker
    ALTER COLUMN brevtype DROP DEFAULT;

-- Legger til en mer presis dokumentklassifisering enn type/distribusjonstype.
ALTER TABLE dokument
    ADD COLUMN IF NOT EXISTS brevtype text;

-- Tilbakefyll kun entydige historiske tilfeller.
UPDATE dokument
SET brevtype = 'VEDTAK'
WHERE brevtype IS NULL
  AND type = 'VEDTAK'
  AND vedtakId IS NOT NULL
  AND søknadId IS NULL
  AND revurderingId IS NULL
  AND klageId IS NULL;
