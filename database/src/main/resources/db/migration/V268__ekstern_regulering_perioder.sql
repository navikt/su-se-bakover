CREATE TABLE IF NOT EXISTS ekstern_regulering_perioder (
    id UUID PRIMARY KEY,
    kjoering_id UUID NOT NULL,
    saksnummer BIGINT NOT NULL,
    tilhoerer TEXT NOT NULL,
    ekstern_kilde TEXT NOT NULL,
    perioder JSONB NOT NULL,
    opprettet TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ekstern_regulering_perioder_kjoering_id_idx ON ekstern_regulering_perioder (kjoering_id);
CREATE INDEX IF NOT EXISTS ekstern_regulering_perioder_saksnummer_idx ON ekstern_regulering_perioder (saksnummer);
