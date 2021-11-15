create table if not exists klage
(
    id uuid primary key,
    sakid uuid references sak(id),
    opprettet timestamptz not null,
    journalpostid text not null,
    saksbehandler text not null
)