create table if not exists feilutbetalingsvarsel
(
    id uuid primary key,
    opprettet timestamptz not null,
    sakId uuid not null references sak(id),
    behandlingId uuid not null references revurdering(id),
    simulering jsonb,
    feilutbetalingslinje jsonb,
    type text not null
);