with revurderinger as (
 select gf.id from grunnlag_formue gf
    join revurdering b on b.id = gf.behandlingid
    join grunnlag_bosituasjon gb on b.id = gb.behandlingid       
    where gf.epsformue is not null and gf.epsformue != 'null'
    and gb.bosituasjontype in (
        'ALENE',
        'MED_VOKSNE',
        'HAR_IKKE_EPS'
    )
) update grunnlag_formue
        set epsformue = null
        where id in (select id from revurderinger);
 
 with søknadsbehandlinger as (
 select gf.id from grunnlag_formue gf
    join behandling b on b.id = gf.behandlingid
    join grunnlag_bosituasjon gb on b.id = gb.behandlingid       
    where gf.epsformue is not null and gf.epsformue != 'null'
    and gb.bosituasjontype in (
        'ALENE',
        'MED_VOKSNE',
        'HAR_IKKE_EPS'
    ) 
) update grunnlag_formue
    set epsformue = null
    where id in (select id from søknadsbehandlinger);