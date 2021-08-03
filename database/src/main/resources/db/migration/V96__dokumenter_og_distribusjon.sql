create table if not exists dokument
(
    id uuid primary key,
    opprettet timestamptz not null,
    sakid uuid not null references sak(id),
    generertDokument bytea not null,
    generertDokumentJson jsonb not null,
    type text not null,
    tittel text not null,
    søknadId uuid references søknad(id),
    vedtakId uuid references vedtak(id),
    revurderingId uuid references revurdering(id),
    bestillbrev boolean not null
);

create table if not exists dokument_distribusjon
(
    id uuid primary key,
    opprettet timestamptz not null,
    endret timestamptz not null,
    dokumentId uuid not null references dokument(id) unique,
    journalpostId text,
    brevbestillingId text
);