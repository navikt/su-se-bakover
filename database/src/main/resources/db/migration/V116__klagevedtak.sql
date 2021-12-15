create table if not exists klagevedtak
(
    id uuid primary key,
    opprettet timestamptz not null,
    type text not null,
    metadata jsonb not null
);
create unique index klagevedtak_metadata_hendelseId_idx on klagevedtak ((metadata->>'hendelseId'));
