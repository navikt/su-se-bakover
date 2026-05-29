-- Utvider ekstern_regulering_perioder med feilkoder slik at vi også kan lagre feilede
-- oppslag fra Pesys og se hvilken feil som oppstod for hvilken sak/tilhører.
ALTER TABLE ekstern_regulering_perioder
    ADD COLUMN IF NOT EXISTS feilkoder JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE ekstern_regulering_perioder
    ALTER COLUMN perioder DROP NOT NULL;

CREATE INDEX IF NOT EXISTS ekstern_regulering_perioder_sak_kjoering_idx
    ON ekstern_regulering_perioder (saksnummer, kjoering_id);

