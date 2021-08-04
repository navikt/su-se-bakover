create table if not exists personhendelse (
    id text primary key,
    opprettet timestamptz not null,
    endret timestamptz not null,
    saksnummer bigint not null,
    hendelse jsonb not null,
    meldingJson jsonb,
    oppgaveId text,
    type text
);