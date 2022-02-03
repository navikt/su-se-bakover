create table if not exists regulering
(
    id uuid primary key,
    opprettet timestamptz not null,
    sakId uuid not null references sak(id),
    beregning jsonb,
    simulering jsonb,
    type text,
    saksbehandler text
);

create table if not exists reguleringsjob
(
    sakId uuid not null references sak(id),
    saksnummer bigint,
    status text,
    saksbehandler text
)