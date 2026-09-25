CREATE TABLE historisk_infotrygd_revurderingsvedtak (
    id UUID PRIMARY KEY,
    revurdering_id UUID NOT NULL UNIQUE REFERENCES historisk_infotrygd_revurdering (id),
    utbetaling_id VARCHAR(30) NOT NULL UNIQUE,
    iverksatt TIMESTAMPTZ NOT NULL,
    attestant TEXT NOT NULL,
    beregning JSONB NOT NULL
);

-- Manuell rollback: dropp historisk_infotrygd_revurderingsvedtak.
