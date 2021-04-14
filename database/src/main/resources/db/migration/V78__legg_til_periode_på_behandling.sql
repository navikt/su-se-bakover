alter table if exists behandling add column stønadsperiode jsonb;

update behandling
    set stønadsperiode = json_build_object('periode', json_build_object('fraOgMed', query.fraOgMed, 'tilOgMed', query.tilOgMed), 'begrunnelse', '')
    from (select id, (beregning->'periode'->>'fraOgMed')::date as fraOgMed,  (beregning->'periode'->>'tilOgMed')::date as tilOgMed from behandling) as query
where behandling.id = query.id;

update behandling
    set stønadsperiode = json_build_object('periode', json_build_object('fraOgMed', (select date_trunc('MONTH', uten_beregning.søknadsdato)::date), 'tilOgMed', (select date_trunc('MONTH', uten_beregning.søknadsdato)::date + interval '1 month' * 12 - interval '1 day')::date), 'begrunnelse', '')
	from (select b.id as behandlingid, s.opprettet as søknadsdato from behandling b
		 	join søknad s on s.id = b.søknadid
		  	and b.beregning is null
		 ) as uten_beregning
where behandling.id = uten_beregning.behandlingid;