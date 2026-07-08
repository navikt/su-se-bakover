ALTER TABLE IF EXISTS kontrollsamtale
    ADD COLUMN IF NOT EXISTS hendelser jsonb NOT NULL DEFAULT '[]'::jsonb;
