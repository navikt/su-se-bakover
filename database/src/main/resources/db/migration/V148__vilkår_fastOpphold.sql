create table if not exists vilkårsvurdering_fastOpphold
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
               when (behandlingsinformasjon -> 'fastOppholdINorge' ->> 'status' = 'VilkårOppfylt') then 'INNVILGET'
               when (behandlingsinformasjon -> 'fastOppholdINorge' ->> 'status' = 'VilkårIkkeOppfylt') then 'AVSLAG'
               when (behandlingsinformasjon -> 'fastOppholdINorge' ->> 'status' = 'Uavklart') then 'UAVKLART'
           end
    from behandling
    where behandlingsinformasjon ->> 'fastOppholdINorge' is not null
)

insert into vilkårsvurdering_fastOpphold(id, opprettet, behandlingid, fraogmed, tilogmed, resultat)
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

insert into vilkårsvurdering_fastOpphold(id, opprettet, behandlingid, fraogmed, tilogmed, resultat)
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

insert into vilkårsvurdering_fastOpphold(id, opprettet, behandlingid, fraogmed, tilogmed, resultat)
    (select uuid_generate_v4(),
            opprettet,
            behandlingId,
            fraogmed,
            tilogmed,
            resultat
     from reguleringer
    ); 