CREATE TABLE IF NOT EXISTS
    institusjonsopphold_hendelse
(
    id          uuid primary key,
    opprettet   timestamptz not null,
    sakId       uuid        not null references sak (id),
    hendelsesId bigint      not null,
    oppholdId bigint      not null,
    norskident  text        not null,
    type        text        not null,
    kilde       text        not null,
    oppgaveId   text default null
)