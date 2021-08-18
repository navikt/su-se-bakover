create table if not exists personhendelse(
     id            uuid        primary key,
     opprettet     timestamptz not null,
     sakId         uuid        not null references sak (id),
     endret        timestamptz not null,
     aktørId       text        not null,
     endringstype  text        not null,
     hendelse      jsonb       not null,
     oppgaveId     text,
     type          text        not null,
     metadata      jsonb       not null
);
create unique index personhendelse_metadata_hendelseId_idx on personhendelse ((metadata->>'hendelseId'));