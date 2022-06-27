with sbehandling (behandlingid, opprettet, fraogmed, tilogmed, resultat, begrunnelse) as (
    select id,
           opprettet,
           (stønadsperiode -> 'periode' ->> 'fraOgMed')::date,
           (stønadsperiode -> 'periode' ->> 'tilOgMed')::date,
           case
               when (behandlingsinformasjon -> 'lovligOpphold' ->> 'status' = 'VilkårOppfylt') then 'INNVILGET'
               when (behandlingsinformasjon -> 'lovligOpphold' ->> 'status' = 'VilkårIkkeOppfylt') then 'AVSLAG'
               when (behandlingsinformasjon -> 'lovligOpphold' ->> 'status' = 'Uavklart') then 'IKKE_VURDERT'
               end,
           case
               when (behandlingsinformasjon ->> 'lovligOpphold' is not null)
                   then behandlingsinformasjon -> 'lovligOpphold' ->> 'begrunnelse'
               end
    from behandling
    where behandlingsinformasjon ->> 'lovligOpphold' is not null
)

insert
into vilkårsvurdering_lovligopphold(id, opprettet, behandlingid, grunnlag_id, fraogmed, tilogmed, resultat, begrunnelse)
    (select uuid_generate_v4(),
            opprettet,
            behandlingId,
            null,
            fraogmed,
            tilogmed,
            resultat,
            begrunnelse
     from sbehandling
    );

with rev (behandlingid, opprettet, fraogmed, tilogmed, resultat, begrunnelse) as (
    select id,
           opprettet,
           (periode ->> 'fraOgMed')::date,
           (periode ->> 'tilOgMed')::date,
           'INNVILGET',
           null
    from revurdering
)
insert
into vilkårsvurdering_lovligOpphold(id, opprettet, behandlingid, grunnlag_id, fraogmed, tilogmed, resultat, begrunnelse)
    (select uuid_generate_v4(),
            opprettet,
            behandlingId,
            null,
            fraogmed,
            tilogmed,
            resultat,
            begrunnelse
     from rev
    );

with reg (behandlingId, opprettet, fraogmed, tilogmed, resultat, begrunnelse) as (
    select id,
           opprettet,
           (periode ->> 'fraOgMed')::date,
           (periode ->> 'tilOgMed')::date,
           'INNVILGET',
           null
    from regulering
)
insert
into vilkårsvurdering_lovligOpphold(id, opprettet, behandlingid, grunnlag_id, fraogmed, tilogmed, resultat, begrunnelse)
    (select uuid_generate_v4(),
            opprettet,
            behandlingId,
            null,
            fraogmed,
            tilogmed,
            resultat,
            begrunnelse
     from reg
    );