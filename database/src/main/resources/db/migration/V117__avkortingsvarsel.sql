create table if not exists avkortingsvarsel
(
    id uuid primary key,
    opprettet timestamptz not null,
    sakId uuid not null references sak(id),
    revurderingId uuid not null references revurdering(id),
    simulering jsonb not null,
    status text not null,
    søknadsbehandlingId uuid references behandling(id)
);