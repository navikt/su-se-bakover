-- På et tidspunkt begynte vi å dobbeltlagre behandlingsinformasjon->formue og formue som grunnlag/vilkår.
-- Sletter disse først, spesielt siden de ikke tar høyde for UAVKLART/MåInnhenteMerInformasjon
delete from vilkårsvurdering_formue vf using behandling b where b.id = vf.behandlingid;
delete from grunnlag_formue vf using behandling b where b.id = vf.behandlingid;

with manglende_grunnlagsdata as (
    select * from behandling where stønadsperiode is not null and (behandlingsinformasjon -> 'formue') != 'null'
),
     mapped_verdier as (select
                            opprettet,
                            id as behandlingid,
                            ( stønadsperiode -> 'periode' ->> 'fraOgMed' )::date as fraogmed,
                            ( stønadsperiode -> 'periode' ->> 'tilOgMed' )::date as tilogmed,
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
                            ( behandlingsinformasjon -> 'formue' -> 'verdier' ) as søkerformue,
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