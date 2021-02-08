create table if not exists revurdering
(
    id uuid primary key,
    opprettet timestamp with time zone not null,
    behandlingId uuid references behandling(id) not null,
    periode jsonb not null,
    beregning jsonb,
    simulering jsonb,
    saksbehandler text not null,
    oppgaveId text,
    revurderingsType text,
    attestant text,
    utbetalingId varchar(30)
);
