create table if not exists regulering
(
    id               uuid primary key,
    sakId            uuid references sak (id) NOT NULL,
    opprettet        timestamptz              not null,
    periode          jsonb                    not null,
    beregning        jsonb,
    simulering       jsonb,
    saksbehandler    text                     not null,
    reguleringStatus text, -- OPPRETTET / IVERKSATT / FEIL (står i loggen :-) )
    reguleringType   text -- AUTOMATISK / MANUELL
);

alter table behandling_vedtak
    add column if not exists reguleringId uuid references regulering (id);

alter table behandling_vedtak
    drop constraint revurderingId_eller_søknadsbehandlingId;
alter table behandling_vedtak
    add constraint revurderingId_eller_søknadsbehandlingId
        CHECK (
                (søknadsbehandlingId IS NOT NULL AND revurderingId IS NULL AND klageId IS NULL AND reguleringId IS NULL)
                OR
                (revurderingId IS NOT NULL AND søknadsbehandlingId IS NULL AND klageId IS NULL AND reguleringId IS NULL)
                OR
                (klageid IS NOT NULL AND søknadsbehandlingId IS NULL AND revurderingId IS NULL AND reguleringId IS NULL)
                OR
                (klageid IS NULL AND søknadsbehandlingId IS NULL AND revurderingId IS NULL AND reguleringId IS NOT NULL)
            );