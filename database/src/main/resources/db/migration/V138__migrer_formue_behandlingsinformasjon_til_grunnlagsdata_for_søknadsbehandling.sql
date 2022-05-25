-- På et tidspunkt begynte vi å dobbeltlagre behandlingsinformasjon->formue og formue som grunnlag/vilkår.
-- Sletter disse først.
delete from vilkårsvurdering_formue vf using behandling b where b.id = vf.behandlingid;
delete from grunnlag_formue vf using behandling b where b.id = vf.behandlingid;

with manglende_grunnlagsdata as (
    select * from behandling
    where not exists
        (select null from grunnlag_formue where grunnlag_formue.behandlingid = behandling.id)
),
     mapped_verdier as (select
                            opprettet,
                            id as behandlingid,
                            ( stønadsperiode ->> 'fraOgMed' )::date as fraogmed,
                            ( stønadsperiode ->> 'tilOgMed' )::date as tilogmed,
                               case
                                   when (behandlingsinformasjon -> 'formue' -> 'epsVerdier' is not null) then jsonb_build_object(
                                       'verdiIkkePrimærbolig', coalesce(cast(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' ->> 'verdiIkkePrimærbolig' ) as integer), 0),
                                       'verdiEiendommer', coalesce(cast(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' ->> 'verdiEiendommer') as integer), 0),
                                       'verdiKjøretøy', coalesce(cast(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' ->> 'verdiKjøretøy' ) as integer), 0),
                                       'innskudd', coalesce(cast(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' ->> 'innskudd' ) as integer), 0),
                                       'verdipapir', coalesce( cast(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' ->> 'verdipapir' ) as integer), 0),
                                       'pengerSkyldt', coalesce(cast(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' ->> 'pengerSkyldt' ) as integer), 0),
                                       'kontanter', coalesce(cast(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' ->> 'kontanter' ) as integer), 0),
                                       'depositumskonto', coalesce(cast(( behandlingsinformasjon -> 'formue' -> 'epsVerdier' ->> 'depositumskonto' ) as integer), 0)
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