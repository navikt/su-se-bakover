CREATE INDEX sak_statistikk_behandling_sekvens_idx
    ON sak_statistikk (behandling_id, id_sekvens);

CREATE INDEX sak_statistikk_funksjonell_tid_idx
    ON sak_statistikk (funksjonell_tid);

CREATE TABLE sak_statistikk_aggregat (
    id UUID PRIMARY KEY,
    maaned DATE NOT NULL UNIQUE,
    status TEXT NOT NULL,
    maks_sekvens_id BIGINT,
    opprettet TIMESTAMPTZ NOT NULL,
    startet TIMESTAMPTZ,
    ferdig TIMESTAMPTZ,
    grunnlag JSONB,
    feilmelding TEXT
);

CREATE INDEX sak_statistikk_aggregat_ventende_idx
    ON sak_statistikk_aggregat (opprettet)
    WHERE status IN ('VENTER', 'FEILET');
