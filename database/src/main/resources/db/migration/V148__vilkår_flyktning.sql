create table if not exists grunnlag_flyktning
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    fraOgMed date not null,
    tilOgMed date not null
);

create table if not exists vilkårsvurdering_flyktning
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    grunnlag_id uuid references grunnlag_flyktning(id),
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
               when (behandlingsinformasjon -> 'flyktning' ->> 'status' = 'VilkårOppfylt') then 'INNVILGET'
               when (behandlingsinformasjon -> 'flyktning' ->> 'status' = 'VilkårIkkeOppfylt') then 'AVSLAG'
               when (behandlingsinformasjon -> 'flyktning' ->> 'status' = 'Uavklart') then 'UAVKLART'
           end
    from behandling
    where behandlingsinformasjon ->> 'flyktning' is not null
)

insert into vilkårsvurdering_flyktning(id, opprettet, behandlingid, grunnlag_id, fraogmed, tilogmed, resultat)
    (select uuid_generate_v4(),
            opprettet,
            behandlingId,
            null,
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

insert into vilkårsvurdering_flyktning(id, opprettet, behandlingid, grunnlag_id, fraogmed, tilogmed, resultat)
    (select uuid_generate_v4(),
            opprettet,
            behandlingId,
            null,
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

insert into vilkårsvurdering_flyktning(id, opprettet, behandlingid, grunnlag_id, fraogmed, tilogmed, resultat)
    (select uuid_generate_v4(),
            opprettet,
            behandlingId,
            null,
            fraogmed,
            tilogmed,
            resultat
     from reguleringer
    );