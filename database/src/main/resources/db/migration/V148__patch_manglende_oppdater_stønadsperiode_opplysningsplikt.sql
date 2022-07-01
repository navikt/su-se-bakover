with behandlinginfo as (
    select
    id,
    (b.stønadsperiode->'periode'->>'fraOgMed')::date as behandlingFom,
    (b.stønadsperiode->'periode'->>'tilOgMed')::date as behandlingTom
    from behandling b
),
grunnlagOgVilkår as (
    select 
        b.id, 
        b.behandlingFom, 
        b.behandlingTom, 
        vo.fraOgMed as vFom, 
        vo.tilOgMed as vTom, 
        go.fraOgMed as gFom, 
        go.tilOgMed as gTom 
    from vilkårsvurdering_opplysningsplikt vo
    join grunnlag_opplysningsplikt go 
        on go.id = vo.grunnlag_id
    join behandlinginfo b 
        on b.id = vo.behandlingid
    where b.behandlingFom != vo.fraOgmed or b.behandlingTom != vo.tilOgMed
), 
oppdatertVilkår as (
    update vilkårsvurdering_opplysningsplikt set
    fraOgMed = behandlingFom,
    tilOgMed = behandlingTom
    from grunnlagOgVilkår where grunnlagOgVilkår.id = behandlingid
    returning behandlingid
),
oppdatertGrunnlag as (
   update grunnlag_opplysningsplikt set
    fraOgMed = behandlingFom,
    tilOgMed = behandlingTom
    from grunnlagOgVilkår where grunnlagOgVilkår.id = behandlingid
    returning behandlingid
) select (select count(*) from oppdatertVilkår) + (select count(*) from oppdatertGrunnlag);