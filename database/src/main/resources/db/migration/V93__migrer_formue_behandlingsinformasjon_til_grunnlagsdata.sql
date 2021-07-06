with manglende_grunnlagsdata as (
    select * from revurdering
    where not exists
        (select null from grunnlag_formue where grunnlag_formue.behandlingid = revurdering.id)
),
     mapped_verdier as (select
                            opprettet,
                            id behandlingid,
                            ( periode ->> 'fraOgMed' )::date as fraogmed,
                            ( periode ->> 'tilOgMed' )::date as tilogmed,
                            behandlingsinformasjon -> 'formue' -> 'epsVerdier' as epsformue,
                            behandlingsinformasjon -> 'formue' -> 'verdier' as søkerformue,
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
