with manglende_grunnlagsdata as (
    select * from revurdering
    where not exists
        (select null from grunnlag_formue where grunnlag_formue.behandlingid = revurdering.id)
),
     mapped_verdier as (select
                            opprettet,
                            id as behandlingid,
                            ( periode ->> 'fraOgMed' )::date as fraogmed,
                            ( periode ->> 'tilOgMed' )::date as tilogmed,
                               case
                                   when (behandlingsinformasjon -> 'formue' -> 'epsVerdier' is not null) then jsonb_build_object(
                                       'verdiIkkePrimærbolig', coalesce(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' -> 'verdiIkkePrimærbolig' )::int, 0),
                                       'verdiEiendommer', coalesce(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' -> 'verdiEiendommer')::int, 0),
                                       'verdiKjøretøy', coalesce(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' -> 'verdiKjøretøy' )::int, 0),
                                       'innskudd', coalesce(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' -> 'innskudd' )::int, 0),
                                       'verdipapir', coalesce(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' -> 'verdipapir' )::int, 0),
                                       'pengerSkyldt', coalesce(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' -> 'pengerSkyldt' )::int, 0),
                                       'kontanter', coalesce(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' -> 'kontanter' )::int, 0),
                                       'depositumskonto', coalesce(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' -> 'depositumskonto' )::int, 0)
                                       )
                                end as epsformue,
                                coalesce( behandlingsinformasjon -> 'formue' -> 'verdier',
                                    jsonb_build_object(
                                        'verdiIkkePrimærbolig', 0,
                                        'verdiEiendommer', 0,
                                        'verdiKjøretøy', 0,
                                        'innskudd', 0,
                                        'verdipapir', 0,
                                        'pengerSkyldt', 0,
                                        'kontanter', 0,
                                        'depositumskonto', 0
                                    ))
                                as søkerformue,
                            ( behandlingsinformasjon -> 'formue' ->> 'begrunnelse' )::text as begrunnelse,
                            behandlingsinformasjon
                        from manglende_grunnlagsdata),
     ny_grunnlag as ( insert into grunnlag_formue (id, opprettet, behandlingid, fraogmed, tilogmed, epsformue, søkerformue, begrunnelse)
         select
             uuid_generate_v4() as id,
             opprettet,
             behandlingid,
             fraogmed,
             tilogmed,
             epsformue,
             søkerformue,
             begrunnelse
         from mapped_verdier
         returning id, behandlingid
     )
insert into vilkårsvurdering_formue (id, opprettet, behandlingid, formue_grunnlag_id, vurdering, resultat, fraogmed, tilogmed)
select uuid_generate_v4(),
       opprettet,
       ny_grunnlag.behandlingid,
       ny_grunnlag.id,
       'AUTOMATISK',
       case
           when (behandlingsinformasjon -> 'formue' ->> 'status' = 'VilkårOppfylt') then 'INNVILGET'
           when (behandlingsinformasjon -> 'formue' ->> 'status' = 'VilkårIkkeOppfylt') then 'AVSLAG'
           else 'UAVKLART'
           end,
       fraogmed,
       tilogmed
from mapped_verdier left join ny_grunnlag on mapped_verdier.behandlingid = ny_grunnlag.behandlingid;
