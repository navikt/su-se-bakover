with sbehandling (behandlingid, opprettet, fraogmed, tilogmed, resultat, begrunnelse) as (
    select id,
           opprettet,
           (stønadsperiode -> 'periode' ->> 'fraOgMed')::date,
            (stønadsperiode -> 'periode' ->> 'tilOgMed')::date,
           case
               when (behandlingsinformasjon -> 'oppholdIUtlandet' ->> 'status' = 'SkalHoldeSegINorge')
                   then
                   'INNVILGET'
               when (behandlingsinformasjon -> 'oppholdIUtlandet' ->> 'status' = 'SkalVæreMerEnn90DagerIUtlandet')
                   then
                   'AVSLAG'
               when (behandlingsinformasjon -> 'oppholdIUtlandet' ->> 'status' = 'Uavklart')
                   then
                   'IKKE_VURDERT'
               end,
           case
               when (behandlingsinformasjon ->> 'oppholdIUtlandet' is not null)
                   then
                           behandlingsinformasjon -> 'oppholdIUtlandet' ->> 'begrunnelse'
               end
    from behandling
    where behandlingsinformasjon ->> 'oppholdIUtlandet' is not null
    )

insert into vilkårsvurdering_utland(id, opprettet, behandlingid, fraogmed, tilogmed, resultat, begrunnelse)
(select uuid_generate_v4(),
    opprettet,
    behandlingId,
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
insert into vilkårsvurdering_utland(id, opprettet, behandlingid, fraogmed, tilogmed, resultat, begrunnelse)
(select uuid_generate_v4(),
    opprettet,
    behandlingId,
    fraogmed,
    tilogmed,
    resultat,
    begrunnelse
    from rev
    );