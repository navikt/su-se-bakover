-- Manuell rollback:
-- DROP TABLE sak_statistikk_aggregat;
-- DROP INDEX sak_statistikk_behandling_sekvens_idx;
-- DROP INDEX sak_statistikk_funksjonell_tid_idx;

CREATE INDEX sak_statistikk_behandling_sekvens_idx
    ON sak_statistikk (behandling_id, id_sekvens);

CREATE INDEX sak_statistikk_funksjonell_tid_idx
    ON sak_statistikk (funksjonell_tid);

CREATE TABLE sak_statistikk_aggregat (
    id UUID PRIMARY KEY,
    fra_og_med DATE NOT NULL,
    til_og_med DATE NOT NULL,
    opplosning TEXT NOT NULL,
    status TEXT NOT NULL,
    versjon INTEGER NOT NULL,
    maks_sekvens_id BIGINT,
    opprettet TIMESTAMPTZ NOT NULL,
    startet TIMESTAMPTZ,
    ferdig TIMESTAMPTZ,
    payload JSONB,
    feilmelding TEXT,
    CONSTRAINT sak_statistikk_aggregat_periode
        UNIQUE (fra_og_med, til_og_med, opplosning)
);

CREATE INDEX sak_statistikk_aggregat_ventende_idx
    ON sak_statistikk_aggregat (opprettet)
    WHERE status IN ('VENTER', 'FEILET');
