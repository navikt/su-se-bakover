CREATE TABLE historisk_infotrygd_revurdering (
    id UUID PRIMARY KEY,
    sak_id UUID NOT NULL REFERENCES sak (id),
    projeksjon_id UUID NOT NULL REFERENCES historisk_alder_projeksjon (id),
    fra_og_med DATE NOT NULL,
    til_og_med DATE NOT NULL,
    status TEXT NOT NULL,
    saksbehandler TEXT NOT NULL,
    versjon BIGINT NOT NULL DEFAULT 0,
    opprettet TIMESTAMPTZ NOT NULL,
    oppdatert TIMESTAMPTZ NOT NULL,
    begrunnelse TEXT,
    vedtak_som_revurderes_maanedsvis JSONB NOT NULL,
    beregning JSONB,
    attesteringer JSONB NOT NULL DEFAULT '[]'::JSONB,
    CHECK (fra_og_med <= til_og_med)
);

-- Manuell rollback: dropp historisk_infotrygd_revurdering.
