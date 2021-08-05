create table if not exists personhendelse (
    id text primary key,
    meldingoffset bigint not null unique,
    opprettet timestamptz not null,
    endret timestamptz not null,
    aktørId text not null,
    endringstype text not null,
    saksnummer bigint not null,
    hendelse jsonb not null,
    oppgaveId text,
    type text not null
);