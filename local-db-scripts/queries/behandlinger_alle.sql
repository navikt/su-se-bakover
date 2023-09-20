-- Uttrekk hentet 2023-09-20 mellom 13 og 14.
-- alle

-- De forskjellige vedtakstypene (STANS_AV_YTELSE,SØKNAD,OPPHØR,ENDRING,REGULERING,GJENOPPTAK_AV_YTELSE,AVVIST_KLAGE,AVSLAG)
select distinct(vedtaktype) from vedtak;

-- Antall søknadsbehandlinger som er iverksatt (SØKNAD,AVSLAG)
select count(*) from behandling b
                         join behandling_vedtak bv on bv.søknadsbehandlingid = b.id
                         join vedtak v on bv.vedtakid = v.id; --1732

-- Antall søknadsbehandlinger som er avslått
select count(*) from behandling b
                         join behandling_vedtak bv on bv.søknadsbehandlingid = b.id
                         join vedtak v on bv.vedtakid = v.id
where v.vedtaktype = 'AVSLAG'; --593

-- Antall søknadsbehandlinger som er innvilget
select count(*) from behandling b
                         join behandling_vedtak bv on bv.søknadsbehandlingid = b.id
                         join vedtak v on bv.vedtakid = v.id
where v.vedtaktype = 'SØKNAD'; --1139

-- Antall avslag pga. formue
select count(distinct v.id) from behandling b
                                     join vilkårsvurdering_formue vf on vf.behandlingId = b.id
                                     join behandling_vedtak bv on bv.søknadsbehandlingid = b.id
                                     join vedtak v on bv.vedtakid = v.id
where vf.resultat = 'AVSLAG'
  and v.vedtaktype = 'AVSLAG'; --103
