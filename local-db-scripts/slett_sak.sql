with
    sakz as (delete from sak where saksnummer in (XXXX) returning id),
    søknadz as (delete from søknad where sakid in (select id from sakz) returning sakid),
    behandlingz as (delete from behandling where sakid in (select id from sakz) returning id, sakid),
    revurderingz as (delete from revurdering where sakid in (select id from sakz) returning id, sakid),
    reguleringz as (delete from regulering where sakid in (select id from sakz) returning id, sakid),
    klagez as (delete from klage where sakid in (select id from sakz) returning id, sakid),
    gr_bosituasjonz as (delete from grunnlag_bosituasjon where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    gr_fradragz as (delete from grunnlag_fradrag where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz))  returning id, behandlingid),
    gr_uførez as (delete from grunnlag_uføre where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz))  returning id, behandlingid),
    vv_uførez as (delete from vilkårsvurdering_uføre where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz))  returning id, behandlingid),
    gr_utlandz as (delete from grunnlag_utland where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    vv_utlandz as (delete from vilkårsvurdering_utland where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    gr_formuez as (delete from grunnlag_formue where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz))  returning id, behandlingid),
    vv_formuez as (delete from vilkårsvurdering_formue where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    gr_opplysningspliktz as (delete from grunnlag_opplysningsplikt where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    vv_opplysningspliktz as (delete from vilkårsvurdering_opplysningsplikt where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    gr_pensjonz as (delete from grunnlag_pensjon where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    vv_pensjonz as (delete from vilkårsvurdering_pensjon where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    gr_lovligoppholdz as (delete from grunnlag_lovligopphold where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    vv_lovligoppholdz as (delete from vilkårsvurdering_lovligopphold where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    vv_familiegjenforeningz as (delete from vilkårsvurdering_familiegjenforening where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    vv_flyktningz as (delete from vilkårsvurdering_flyktning where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    bvz as (delete from behandling_vedtak where sakid in (select id from sakz) returning sakid, vedtakid, søknadsbehandlingid, revurderingid, klageid, reguleringid),
    vedtakz as (delete from vedtak where id in (select vedtakid from bvz) returning id),
    utbetalingz as (delete from utbetaling where sakid in (select id from sakz) returning id, sakid),
    utbetalingslinjez as (delete from utbetalingslinje where utbetalingid in (select id from utbetalingz) returning utbetalingid),
    dokumentz as (delete from dokument where sakid in (select id from sakz) returning id, sakid),
    dokdistz as (delete from dokument_distribusjon where dokumentid in (select id from dokumentz) returning dokumentid),
    kontrollz as (delete from kontrollsamtale where sakid in (select id from sakz) returning id, sakid),
    tilbakekrevingz as (delete from tilbakekrevingsbehandling where sakid in (select id from sakz) returning id, sakid),
    avkortingz as (delete from avkortingsvarsel where sakid in (select id from sakz) returning id, sakid),
    personhendelsez as (delete from personhendelse where sakid in (select id from sakz) returning sakid),
    klagehendelsez as (delete from klageinstanshendelse where utlest_klageid in (select id from klagez) returning id, utlest_klageid),
    hendelsez as (delete from hendelse where sakid in (select id from sakz) returning sakid),
    gr_personlig_oppmøte as (delete from grunnlag_personlig_oppmøte where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    vv_fastopphold as (delete from vilkårsvurdering_fastopphold where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    vv_institusjonsopphold as (delete from vilkårsvurdering_institusjonsopphold where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    vv_personlig_oppmøte as (delete from vilkårsvurdering_personlig_oppmøte where behandlingid in ((select id from behandlingz) union (select id from revurderingz) union (select id from reguleringz)) returning id, behandlingid),
    dokument_skatt as (delete from dokument_skatt where sakid in (select id from sakz) returning sakid),
    skatt as (delete from skatt where sakid in (select id from sakz) returning sakid)
select sakz.id
from sakz
         left join søknadz on sakz.id = søknadz.sakid
         left join behandlingz on sakz.id = behandlingz.sakid
         left join revurderingz on sakz.id = revurderingz.sakid
         left join reguleringz on sakz.id = reguleringz.sakid
         left join klagez on sakz.id = klagez.sakid
         left join gr_bosituasjonz on behandlingz.id = gr_bosituasjonz.behandlingid
         left join gr_fradragz on behandlingz.id = gr_fradragz.behandlingid
         left join gr_uførez on behandlingz.id = gr_uførez.behandlingid
         left join vv_uførez on behandlingz.id = vv_uførez.behandlingid
         left join gr_utlandz on behandlingz.id = gr_utlandz.behandlingid
         left join vv_utlandz on behandlingz.id = vv_utlandz.behandlingid
         left join gr_formuez on behandlingz.id = gr_formuez.behandlingid
         left join vv_formuez on behandlingz.id = vv_formuez.behandlingid
         left join gr_opplysningspliktz on behandlingz.id = gr_opplysningspliktz.behandlingid
         left join vv_opplysningspliktz on behandlingz.id = vv_opplysningspliktz.behandlingid
         left join gr_pensjonz on behandlingz.id = gr_pensjonz.behandlingid
         left join vv_pensjonz on behandlingz.id = vv_pensjonz.behandlingid
         left join gr_lovligoppholdz on behandlingz.id = gr_lovligoppholdz.behandlingid
         left join vv_lovligoppholdz on behandlingz.id = vv_lovligoppholdz.behandlingid
         left join vv_familiegjenforeningz on behandlingz.id = vv_familiegjenforeningz.behandlingid
         left join vv_flyktningz on behandlingz.id = vv_flyktningz.behandlingid
         left join bvz on sakz.id = bvz.sakid
         left join vedtakz on bvz.vedtakid = vedtakz.id
         left join utbetalingz on sakz.id = utbetalingz.sakid
         left join utbetalingslinjez on utbetalingid = utbetalingz.id
         left join dokumentz on sakz.id = dokumentz.sakid
         left join dokdistz on dokumentz.id = dokdistz.dokumentid
         left join kontrollz on sakz.id = kontrollz.sakid
         left join tilbakekrevingz on sakz.id = tilbakekrevingz.sakid
         left join avkortingz on sakz.id = avkortingz.sakid
         left join personhendelsez on sakz.id = personhendelsez.sakid
         left join klagehendelsez on utlest_klageid = klagehendelsez.utlest_klageid
         left join hendelsez on sakz.id = hendelsez.sakid
         left join gr_personlig_oppmøte on behandlingz.id = gr_personlig_oppmøte.behandlingid
         left join vv_fastopphold on behandlingz.id = vv_fastopphold.behandlingid
         left join vv_institusjonsopphold on behandlingz.id = vv_institusjonsopphold.behandlingid
         left join vv_personlig_oppmøte on behandlingz.id = vv_personlig_oppmøte.behandlingid
         left join dokument_skatt on sakz.id = dokument_skatt.sakid
         left join skatt on sakz.id = skatt.sakid
;