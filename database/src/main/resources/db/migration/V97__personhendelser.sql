create table if not exists personhendelse(
     id            uuid        primary key,
     opprettet     timestamptz not null,
     sakId         uuid        not null references sak (id),
     hendelseId    text        not null unique,
     meldingoffset bigint      not null, -- only unique within a partition
     endret        timestamptz not null,
     aktørId       text        not null,
     endringstype  text        not null,
     hendelse      jsonb       not null,
     oppgaveId     text,
     type          text        not null
);