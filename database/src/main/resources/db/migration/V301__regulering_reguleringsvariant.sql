-- Lagrer reguleringsvariant (GRUNNBELØP / ALDERSFRADRAG) på regulering.
-- Eksisterende rader er grunnbeløpsreguleringer, derfor GRUNNBELØP som default.
ALTER TABLE regulering ADD COLUMN IF NOT EXISTS reguleringsvariant TEXT NOT NULL DEFAULT 'GRUNNBELØP';