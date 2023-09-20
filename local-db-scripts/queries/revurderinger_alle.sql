-- Uttrekk hentet 2023-09-20 mellom 13 og 14.
-- alle

-- De forskjellige vedtakstypene (STANS_AV_YTELSE,SØKNAD,OPPHØR,ENDRING,REGULERING,GJENOPPTAK_AV_YTELSE,AVVIST_KLAGE,AVSLAG)
select distinct(vedtaktype) from vedtak;

-- Antall revurderinger som er iverksatt (STANS_AV_YTELSE,OPPHØR,ENDRING,GJENOPPTAK_AV_YTELSE)
select count(*) from revurdering r
                         join behandling_vedtak bv on bv.revurderingid = r.id
                         join vedtak v on bv.vedtakid = v.id; --1059

-- Antall revurderinger som har ført til stans
select count(*) from revurdering r
                         join behandling_vedtak bv on bv.revurderingid = r.id
                         join vedtak v on bv.vedtakid = v.id
where v.vedtaktype = 'STANS_AV_YTELSE'; --326

-- Antall revurderinger som har ført til gjenopptak
select count(*) from revurdering r
                         join behandling_vedtak bv on bv.revurderingid = r.id
                         join vedtak v on bv.vedtakid = v.id
where v.vedtaktype = 'GJENOPPTAK_AV_YTELSE'; --240

-- Antall revurderinger som har ført til innvilgelse
select count(*) from revurdering r
                         join behandling_vedtak bv on bv.revurderingid = r.id
                         join vedtak v on bv.vedtakid = v.id
where v.vedtaktype = 'ENDRING'; --367

-- Antall revurderinger som har ført til opphør
select count(*) from revurdering r
                         join behandling_vedtak bv on bv.revurderingid = r.id
                         join vedtak v on bv.vedtakid = v.id
where v.vedtaktype = 'OPPHØR'; --126

-- Antall opphør pga. formue
select count(distinct v.id) from revurdering r
                                     join vilkårsvurdering_formue vf on vf.behandlingId = r.id
                                     join behandling_vedtak bv on bv.revurderingid = r.id
                                     join vedtak v on bv.vedtakid = v.id
where vf.resultat = 'AVSLAG'
  and v.vedtaktype = 'OPPHØR'; --31