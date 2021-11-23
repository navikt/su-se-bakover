create table if not exists klage
(
    id uuid primary key,
    sakid uuid references sak(id),
    opprettet timestamptz not null,
    journalpostid text not null,
    saksbehandler text not null,
    type text not null,
    vedtakId uuid references vedtak(id) null default null,
    innenforFristen bool null default null,
    klagesDetPåKonkreteElementerIVedtaket bool null default null,
    erUnderskrevet bool null default null,
    begrunnelse text null default null,
    fritekstTilBrev text null default null,
    vedtaksvurdering jsonb null default null
)
