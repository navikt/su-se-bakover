-- Uttrekk hentet 2023-09-20 mellom 13 og 14.
-- 2023

-- De forskjellige vedtakstypene (STANS_AV_YTELSE,SØKNAD,OPPHØR,ENDRING,REGULERING,GJENOPPTAK_AV_YTELSE,AVVIST_KLAGE,AVSLAG)
select distinct(vedtaktype) from vedtak;

-- Antall søknadsbehandlinger som er iverksatt (SØKNAD,AVSLAG)
select count(*) from behandling b
                         join behandling_vedtak bv on bv.søknadsbehandlingid = b.id
                         join vedtak v on bv.vedtakid = v.id
where v.opprettet > '2023-01-01'::timestamptz; --776


-- Antall søknadsbehandlinger som er avslått
select count(*) from behandling b
                         join behandling_vedtak bv on bv.søknadsbehandlingid = b.id
                         join vedtak v on bv.vedtakid = v.id
where v.vedtaktype = 'AVSLAG'
  and v.opprettet > '2023-01-01'::timestamptz; --244

-- Antall søknadsbehandlinger som er innvilget
select count(*) from behandling b
                         join behandling_vedtak bv on bv.søknadsbehandlingid = b.id
                         join vedtak v on bv.vedtakid = v.id
where v.vedtaktype = 'SØKNAD'
  and v.opprettet > '2023-01-01'::timestamptz; --532

-- Antall avslag pga. formue
select count(distinct v.id) from behandling b
                                     join vilkårsvurdering_formue vf on vf.behandlingId = b.id
                                     join behandling_vedtak bv on bv.søknadsbehandlingid = b.id
                                     join vedtak v on bv.vedtakid = v.id
where vf.resultat = 'AVSLAG'
  and v.vedtaktype = 'AVSLAG'
  and v.opprettet > '2023-01-01'::timestamptz; --43
