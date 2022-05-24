create table if not exists grunnlag_opplysningsplikt
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    fraOgMed date not null,
    tilOgMed date not null,
    beskrivelse jsonb not null
);

create table if not exists vilkårsvurdering_opplysningsplikt
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    grunnlag_id uuid references grunnlag_opplysningsplikt(id),
    resultat text not null,
    fraOgMed date not null,
    tilOgMed date not null
);

with
medVedtak as (
	select
		b.id,
		v.vedtaktype,
		v.avslagsgrunner,
		stønadsperiode->'periode'->>'fraOgMed' as fom,
		stønadsperiode->'periode'->>'tilOgMed' as tom,
		b.opprettet
	from behandling b
		join behandling_vedtak bv on bv.søknadsbehandlingid = b.id
		join vedtak v on bv.vedtakid = v.id
			union
	select
		b.id,
		v.vedtaktype,
		v.avslagsgrunner,
		periode->>'fraOgMed' as fom,
		periode->>'tilOgMed' as tom,
		b.opprettet
	from revurdering b
		join behandling_vedtak bv on bv.revurderingid = b.id
		join vedtak v on bv.vedtakid = v.id
    union
	    select
            b.id,
            v.vedtaktype,
            null,
            periode->>'fraOgMed' as fom,
            periode->>'tilOgMed' as tom,
            b.opprettet
    from regulering b join behandling_vedtak bv on b.id = bv.reguleringid join vedtak v on bv.vedtakid = v.id

),
utenVedtak as (
	select
		b.id,
		stønadsperiode->'periode'->>'fraOgMed' as fom,
		stønadsperiode->'periode'->>'tilOgMed' as tom,
		b.opprettet
	from behandling b
		where b.id not in (select id from medVedtak) and not lukket and stønadsperiode is not null
			union
	select
		b.id,
		periode->>'fraOgMed' as fom,
		periode->>'tilOgMed' as tom,
		b.opprettet
	from revurdering b
		where b.id not in (select id from medVedtak) and avsluttet is null
),
alle as (
	select
		id,
		vedtaktype,
		avslagsgrunner,
		fom,
		tom,
		opprettet
	from medVedtak
		union
	select
		id,
		null,
		null,
		fom,
		tom,
		opprettet
	from utenVedtak
)
--select * from alle;
,
grunnlag as (
	insert into grunnlag_opplysningsplikt(
		id,
		opprettet,
		behandlingId,
		fraOgMed,
		tilOgMed,
		beskrivelse
	)(
		select
			uuid_generate_v4(),
			opprettet,
			id,
			fom::date,
			tom::date,
			case when
				avslagsgrunner like '%MANGLENDE_DOKUMENTASJON%' then to_json('{"type":"UtilstrekkeligDokumentasjon"}'::json)
				else to_json('{"type":"TilstrekkeligDokumentasjon"}'::json)
			end
		from alle
	)
	returning
		id,
		opprettet,
		behandlingId,
		fraOgMed,
		tilOgMed,
		beskrivelse
),
vilkår as (
	insert into vilkårsvurdering_opplysningsplikt(
		id,
		opprettet,
		behandlingId,
		grunnlag_id,
		resultat,
		fraOgMed,
		tilOgMed
	)(
		select
			uuid_generate_v4(),
			opprettet,
			behandlingid,
			id,
			case when
				beskrivelse = '{"type":"UtilstrekkeligDokumentasjon"}' then 'AVSLAG'
				else 'INNVILGET'
			end,
			fraOgMed,
			tilOgMed
		from grunnlag
	)
	returning
		id
),
forventet as (
	select sum(antall) as antall from (
		select count(*) as antall from behandling where not lukket and stønadsperiode is not null
		union all
		select count(*) as antall from revurdering where avsluttet is null
        union all
        select count(*) as antall from regulering where regulering.reguleringstatus = 'IVERKSATT'
	) as antallTotalt
),
nyeGrunnlag as (
	select count(*) as antall from grunnlag
),
nyeVilkår as (
	select count(*) as antall from vilkår
) select
	forventet.antall,
	nyeGrunnlag.antall,
	nyeVilkår.antall,
	(case when forventet.antall = nyeGrunnlag.antall and nyeGrunnlag.antall = nyeVilkår.antall then true
	else false end) as suksess
	from forventet, nyeGrunnlag, nyeVilkår;