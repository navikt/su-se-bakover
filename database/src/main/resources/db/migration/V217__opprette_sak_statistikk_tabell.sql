CREATE TABLE sak_statistikk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hendelse_tid TIMESTAMPTZ NOT NULL,
    teknisk_tid TIMESTAMPTZ NOT NULL,
    sak_id UUID NOT NULL,
    saksnummer BIGINT NOT NULL,
    behandling_id UUID NOT NULL,
    relatert_behandling_id UUID,
    aktorid VARCHAR NOT NULL,

    sak_ytelse TEXT NOT NULL,
    sak_utland TEXT NOT NULL,
    behandling_type TEXT NOT NULL,
    behandling_metode TEXT NOT NULL,

    mottatt_tid TIMESTAMPTZ NOT NULL,
    registrert_tid TIMESTAMPTZ NOT NULL,
    ferdigbehandlet_tid TIMESTAMPTZ,
    utbetalt_tid DATE,

    behandling_status TEXT NOT NULL,
    behandling_resultat TEXT,
    behandling_begrunnelse TEXT,
    behandling_aarsak TEXT,

    opprettet_av TEXT NOT NULL,
    saksbehandler TEXT,
    ansvarlig_beslutter TEXT,
    ansvarlig_enhet TEXT,
    vedtakslosning_navn TEXT,

    funksjonell_periode_fom DATE,
    funksjonell_periode_tom DATE,
    tilbakekrev_beloep BIGINT
);