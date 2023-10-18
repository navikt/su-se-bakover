CREATE TABLE IF NOT EXISTS hendelse_fil
(
    id         uuid primary key,
    hendelseId uuid references hendelse (hendelseid) not null,
    sakId      uuid references sak (id),
    opprettet  timestamptz not null DEFAULT now(),
    data       bytea       not null
);


comment on table
    hendelse_fil
is
    'Tabell som skal holde på dataen for en hendelse. Det kan for eksempel være en PDF fra et dokument'