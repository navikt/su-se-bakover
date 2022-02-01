alter table behandling_vedtak
    add column if not exists klageId uuid references klage (id);

alter table behandling_vedtak
    drop constraint revurderingId_eller_søknadsbehandlingId;
alter table behandling_vedtak
    add constraint  revurderingId_eller_søknadsbehandlingId
        CHECK (
            (søknadsbehandlingId IS NOT NULL AND revurderingId IS NULL AND klageId IS NULL)
            OR
            (revurderingId IS NOT NULL AND søknadsbehandlingId IS NULL AND klageId IS NULL)
            OR
            (klageid IS NOT NULL AND søknadsbehandlingId IS NULL AND revurderingId IS NULL)
        );

alter table vedtak
    alter column fraogmed drop not null;

alter table vedtak
    alter column tilogmed drop not null;
