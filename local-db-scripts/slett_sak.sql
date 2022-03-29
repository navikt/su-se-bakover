with
    sakz as (delete from sak where saksnummer in (XXXX) returning id),
    søknadz as (delete from søknad where sakid in (select id from sakz) returning sakid),
    behandlingz as (delete from behandling where sakid in (select id from sakz) returning id, sakid),
    utbetalingz as (delete from utbetaling where sakid in (select id from sakz) returning id, sakid),
    bvz as (delete from behandling_vedtak where sakid in (select id from sakz) returning sakid, vedtakid, søknadsbehandlingid, revurderingid, klageid),
    dokumentz as (delete from dokument where sakid in (select id from sakz) returning id, sakid),
    kontrollz as (delete from kontrollsamtale where sakid in (select id from sakz) returning id, sakid),
    vedtakz as (delete from vedtak where id in (select vedtakid from bvz) returning id),
    dokdistz as (delete from dokument_distribusjon where dokumentid in (select id from dokumentz) returning dokumentid),
    revurderingz as (delete from revurdering where vedtaksomrevurderesid in (select id from vedtakz) returning id, vedtaksomrevurderesid),
    klagez as (delete from klage where sakid in (select id from sakz) returning sakid),
    avkortingz as (delete from avkortingsvarsel where sakid in (select id from sakz) returning sakid),
    gr_bosituasjonz as (delete from grunnlag_bosituasjon where behandlingid in ((select id from behandlingz) union (select id from revurderingz)) returning id, behandlingid),
    gr_formuez as (delete from grunnlag_formue where behandlingid in ((select id from behandlingz) union (select id from revurderingz)) returning id, behandlingid),
    gr_fradragz as (delete from grunnlag_fradrag where behandlingid in ((select id from behandlingz) union (select id from revurderingz)) returning id, behandlingid),
    gr_uførez as (delete from grunnlag_uføre where behandlingid in ((select id from behandlingz) union (select id from revurderingz)) returning id, behandlingid),
    gr_utlandz as (delete from grunnlag_utland where behandlingid in ((select id from behandlingz) union (select id from revurderingz)) returning id, behandlingid),
    vv_formue as (delete from vilkårsvurdering_formue where behandlingid in ((select id from behandlingz) union (select id from revurderingz)) returning id, behandlingid),
    vv_uføre as (delete from vilkårsvurdering_uføre where behandlingid in ((select id from behandlingz) union (select id from revurderingz)) returning id, behandlingid),
    vv_utland as (delete from vilkårsvurdering_utland where behandlingid in ((select id from behandlingz) union (select id from revurderingz)) returning id, behandlingid),
    personhendelsez as (delete from personhendelse where sakid in (select id from sakz) returning sakid)
select sakz.id
from sakz
         left join søknadz on sakz.id = søknadz.sakid
         left join behandlingz on sakz.id = behandlingz.sakid
         left join utbetalingz on sakz.id = utbetalingz.sakid
         left join bvz on sakz.id = bvz.sakid
         left join dokumentz on sakz.id = dokumentz.sakid
         left join kontrollz on sakz.id = kontrollz.sakid
         left join vedtakz on bvz.vedtakid = vedtakz.id
         left join dokdistz on dokumentz.id = dokdistz.dokumentid
         left join revurderingz on vedtakz.id = revurderingz.vedtaksomrevurderesid
         left join klagez on sakz.id = klagez.sakid
         left join avkortingz on sakz.id = avkortingz.sakid
         left join gr_bosituasjonz on behandlingz.id = gr_bosituasjonz.behandlingid
         left join gr_formuez on behandlingz.id = gr_formuez.behandlingid
         left join gr_fradragz on behandlingz.id = gr_fradragz.behandlingid
         left join gr_uførez on behandlingz.id = gr_uførez.behandlingid
         left join gr_utlandz on behandlingz.id = gr_utlandz.behandlingid
         left join vv_formue on behandlingz.id = vv_formue.behandlingid
         left join vv_uføre on behandlingz.id = vv_uføre.behandlingid
         left join vv_utland on behandlingz.id = vv_utland.behandlingid
         left join personhendelsez on sakz.id = personhendelsez.sakid
;