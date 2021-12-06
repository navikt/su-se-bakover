create table if not exists klage
(
    id uuid primary key,
    sakid uuid references sak(id),
    opprettet timestamptz not null,
    journalpostid text not null,
    saksbehandler text not null,
    datoKlageMottatt date not null,
    type text not null,
    attestering jsonb not null default '[]'::jsonb,
    vedtakId uuid references vedtak(id) null default null,
    innenforFristen text null default null,
    klagesDetPåKonkreteElementerIVedtaket bool null default null,
    erUnderskrevet text null default null,
    begrunnelse text null default null,
    fritekstTilBrev text null default null,
    vedtaksvurdering jsonb null default null
);

ALTER TABLE dokument
    ADD COLUMN IF NOT EXISTS
        klageId uuid references klage(id);
