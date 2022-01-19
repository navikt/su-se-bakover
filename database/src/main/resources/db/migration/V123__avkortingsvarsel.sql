create table if not exists avkortingsvarsel
(
    id uuid primary key,
    opprettet timestamptz not null,
    sakId uuid not null references sak(id),
    revurderingId uuid not null references revurdering(id),
    simulering jsonb not null,
    status text not null,
    behandlingId uuid
);

alter table behandling add column avkorting json;
alter table revurdering add column avkorting json;