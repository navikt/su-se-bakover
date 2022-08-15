-- behandlingsinformasjon fjernes fra kildekoden, men vi beholder de historiske dataene inntil videre.
alter table behandling
    alter column behandlingsinformasjon drop not null;

create table if not exists vilkårsvurdering_institusjonsopphold
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    resultat text not null,
    fraOgMed date not null,
    tilOgMed date not null
);


with søknadsbehandlinger (behandlingid, opprettet, fraogmed, tilogmed, resultat) as (
    select id,
           opprettet,
           (stønadsperiode -> 'periode' ->> 'fraOgMed')::date,
           (stønadsperiode -> 'periode' ->> 'tilOgMed')::date,
           case
               when (behandlingsinformasjon -> 'institusjonsopphold' ->> 'status' = 'VilkårOppfylt') then 'INNVILGET'
               when (behandlingsinformasjon -> 'institusjonsopphold' ->> 'status' = 'VilkårIkkeOppfylt') then 'AVSLAG'
               when (behandlingsinformasjon -> 'institusjonsopphold' ->> 'status' = 'Uavklart') then 'UAVKLART'
               end
    from behandling
    where behandlingsinformasjon ->> 'institusjonsopphold' is not null
)

insert into vilkårsvurdering_institusjonsopphold(id, opprettet, behandlingid, fraogmed, tilogmed, resultat)
    (select uuid_generate_v4(),
            opprettet,
            behandlingId,
            fraogmed,
            tilogmed,
            resultat
     from søknadsbehandlinger
    );

with revurderinger (behandlingid, opprettet, fraogmed, tilogmed, resultat) as (
    select id,
           opprettet,
           (periode ->> 'fraOgMed')::date,
           (periode ->> 'tilOgMed')::date,
           'INNVILGET'
    from revurdering
)

insert into vilkårsvurdering_institusjonsopphold(id, opprettet, behandlingid, fraogmed, tilogmed, resultat)
    (select uuid_generate_v4(),
            opprettet,
            behandlingId,
            fraogmed,
            tilogmed,
            resultat
     from revurderinger
    );

with reguleringer (behandlingId, opprettet, fraogmed, tilogmed, resultat) as (
    select id,
           opprettet,
           (periode ->> 'fraOgMed')::date,
           (periode ->> 'tilOgMed')::date,
           'INNVILGET'
    from regulering
)

insert into vilkårsvurdering_institusjonsopphold(id, opprettet, behandlingid, fraogmed, tilogmed, resultat)
    (select uuid_generate_v4(),
            opprettet,
            behandlingId,
            fraogmed,
            tilogmed,
            resultat
     from reguleringer
    );