with uførhetUtenOpplysninger (behandlingsid, behandlingsresultat, uførebegrunnelse, id, opprettet, fom, tom, uføregrad,
                              forventetinntekt) as (
    select b.id,
           case
               when (b.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'VilkårOppfylt') then 'INNVILGET'
               when (b.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'VilkårIkkeOppfylt') then 'AVSLAG'
               when (b.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'HarUføresakTilBehandling') then 'UAVKLART'
               end,
           b.behandlingsinformasjon -> 'uførhet' ->> 'begrunnelse',
           uuid_generate_v4(),
           opprettet,
           (stønadsperiode -> 'periode' ->> 'fraOgMed')::date,
           (stønadsperiode -> 'periode' ->> 'tilOgMed')::date,
           null,
           null
    from behandling b
    where behandlingsinformasjon -> 'uførhet' is not null
      and stønadsperiode is not null
      and behandlingsinformasjon -> 'uførhet' ->> 'status' != 'VilkårOppfylt'

    union all

    select r.id,
           case
               when (r.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'VilkårOppfylt') then 'INNVILGET'
               when (r.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'VilkårIkkeOppfylt') then 'AVSLAG'
               when (r.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'HarUføresakTilBehandling') then 'UAVKLART'
               end,
           r.behandlingsinformasjon -> 'uførhet' ->> 'begrunnelse',
           uuid_generate_v4(),
           opprettet,
           (periode -> 'periode' ->> 'fraOgMed')::date,
           (periode -> 'periode' ->> 'tilOgMed')::date,
           null,
           null
    from revurdering r
    where behandlingsinformasjon -> 'uførhet' is not null
      and periode is not null
      and behandlingsinformasjon -> 'uførhet' ->> 'status' != 'VilkårOppfylt')

   , uføregrunnlagsdata (behandlingsid, behandlingsresultat, uførebegrunnelse, id, opprettet, fom, tom, uføregrad,
                         forventetinntekt) as (
    select b.id,
           case
               when (b.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'VilkårOppfylt') then 'INNVILGET'
               when (b.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'VilkårIkkeOppfylt') then 'AVSLAG'
               when (b.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'HarUføresakTilBehandling') then 'UAVKLART'
               end,
           b.behandlingsinformasjon -> 'uførhet' ->> 'begrunnelse',
           uuid_generate_v4(),
           opprettet,
           (stønadsperiode -> 'periode' ->> 'fraOgMed')::date,
           (stønadsperiode -> 'periode' ->> 'tilOgMed')::date,
           (behandlingsinformasjon -> 'uførhet' ->> 'uføregrad')::integer,
           (behandlingsinformasjon -> 'uførhet' ->> 'forventetInntekt')::integer
    from behandling b
    where behandlingsinformasjon -> 'uførhet' ->> 'status' = 'VilkårOppfylt'
      and stønadsperiode != 'null'

    union all
    select r.id,
           case
               when (r.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'VilkårOppfylt') then 'INNVILGET'
               when (r.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'VilkårIkkeOppfylt') then 'AVSLAG'
               when (r.behandlingsinformasjon -> 'uførhet' ->> 'status' = 'HarUføresakTilBehandling') then 'UAVKLART'
               end,
           r.behandlingsinformasjon -> 'uførhet' ->> 'begrunnelse',
           uuid_generate_v4(),
           opprettet,
           (periode -> 'periode' ->> 'fraOgMed')::date,
           (periode -> 'periode' ->> 'tilOgMed')::date,
           (behandlingsinformasjon -> 'uførhet' ->> 'uføregrad')::integer,
           (behandlingsinformasjon -> 'uførhet' ->> 'forventetInntekt')::integer
    from revurdering r
    where behandlingsinformasjon -> 'uførhet' ->> 'status' = 'VilkårOppfylt'
      and periode is not null
)

   , ins_grunnlag_uføre as (insert into grunnlag_uføre (id, opprettet, fraOgMed, tilOgMed, uføregrad, forventetinntekt)
    select id,
           opprettet,
           fom,
           tom,
           uføregrad,
           forventetinntekt
    from uføregrunnlagsdata)

   , ins_behandling_grunnlag
    as (insert into behandling_grunnlag (behandlingid, uføre_grunnlag_id) select behandlingsid, id from uføregrunnlagsdata)

insert into vilkårsvurdering_uføre(id, opprettet, behandlingid, uføre_grunnlag_id, vurdering, resultat, begrunnelse, fraogmed,
                                   tilogmed)
    ( select uuid_generate_v4(),
             opprettet,
             behandlingsid,
             id,
             'MANUELL'::text,
             behandlingsresultat,
             uførebegrunnelse,
             fom,
             tom
      from uføregrunnlagsdata )
union all
( select uuid_generate_v4(),
         opprettet,
         behandlingsid,
         null,
         'MANUELL'::text,
         behandlingsresultat,
         uførebegrunnelse,
         fom,
         tom
  from uførhetUtenOpplysninger );
